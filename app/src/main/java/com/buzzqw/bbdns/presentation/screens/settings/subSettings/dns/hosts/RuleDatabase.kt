/* Copyright (C) 2024 Charles Lombardo <clombardo169@gmail.com>
 *
 * Derived from DNS66:
 * Copyright (C) 2016 - 2019 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts

import androidx.collection.MutableLongList
import androidx.collection.MutableLongSet
import androidx.collection.longSetOf
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.presentation.config
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.Reader
import javax.inject.Singleton

@Singleton
class RuleDatabase {

    companion object {
        private const val IPV4_LOOPBACK = "127.0.0.1"
        private const val IPV6_LOOPBACK = "::1"
        private const val NO_ROUTE = "0.0.0.0"

        @Deprecated("Use BlocklistParser.parseLine() instead")
        fun parseLine(line: String): String? {
            if (line.isEmpty() || line.isBlank()) {
                return null
            }

            var endOfLine = line.indexOf('#')
            if (endOfLine == -1) {
                endOfLine = line.length
            }

            if (endOfLine <= 0) {
                return null
            }

            var startOfHost = 0
            val ipv4LoopbackIndex = line.lastIndexOf(IPV4_LOOPBACK)
            if (ipv4LoopbackIndex != -1) {
                startOfHost += (ipv4LoopbackIndex + IPV4_LOOPBACK.length)
            }
            if (startOfHost == 0) {
                val ipv6LoopbackIndex = line.lastIndexOf(IPV6_LOOPBACK)
                if (ipv6LoopbackIndex != -1) {
                    startOfHost += (ipv6LoopbackIndex + IPV6_LOOPBACK.length)
                }
            }
            if (startOfHost == 0) {
                val noRouteIndex = line.lastIndexOf(NO_ROUTE)
                if (noRouteIndex != -1) {
                    startOfHost += (noRouteIndex + NO_ROUTE.length)
                }
            }

            if (startOfHost >= endOfLine) {
                return null
            }

            while (startOfHost < endOfLine && Character.isWhitespace(line[startOfHost])) {
                startOfHost++
            }
            while (startOfHost < endOfLine && Character.isWhitespace(line[endOfLine - 1])) {
                endOfLine--
            }

            val host = line.substring(startOfHost, endOfLine).lowercase()
            if (host.isEmpty() || host.any { Character.isWhitespace(it) }) {
                return null
            }
            return host
        }

        /**
         * True for lists in the "wildcard domains" format, where an entry also
         * covers every subdomain. Read-only helper shared with BlockReasonFinder.
         */
        fun isWildcardDomainList(item: Host): Boolean =
            item.data.contains("/wildcard/", ignoreCase = true) ||
                item.data.contains("onlydomains", ignoreCase = true)

        /** Entries loaded from one list by the last [initialize] run. */
        data class ListStat(val title: String, val data: String, val state: HostState, val entries: Int)

        private val _listStats = MutableStateFlow<List<ListStat>>(emptyList())
        /** Per-list entry counts of the rules currently loaded, in load order. */
        val listStats: StateFlow<List<ListStat>> = _listStats.asStateFlow()

        /** How a loaded list entry matched a queried domain. */
        enum class HostMatchType { EXACT, PARENT_DOMAIN, TLD }

        /** The list that contributed a matching rule. */
        data class HostSource(val title: String, val type: HostMatchType)

        /**
         * [initialize] rebuilds shared state (rules, allowlist, TLDs): two runs
         * at the same time corrupted it and failed the blocklist download
         * ("Something went wrong") while the other run carried on.
         */
        private val initMutex = Mutex()

        /** True for TLD lists, whose single-label entries block a whole TLD. */
        fun isTldList(item: Host): Boolean =
            item.data.contains("tld", ignoreCase = true) ||
                item.title.contains("tld", ignoreCase = true)
    }

    var blockedHosts = MutableStateFlow(atomic(longSetOf()))

    /**
     * Source index of the last [initialize] run: hash of an exact/suffix domain
     * to (list index + 1), plus the per-list titles. It lets the "why is this
     * blocked" sheet find the list without scanning megabytes of text.
     */
    @Volatile private var exactSources: LongArray = LongArray(0)
    @Volatile private var suffixSources: LongArray = LongArray(0)
    @Volatile private var tldSources: Map<String, Int> = emptyMap()
    @Volatile private var sourceTitles: List<String> = emptyList()
    @Volatile private var patternListTitles: Set<String> = emptySet()
    @Volatile private var whitelistListTitles: Set<String> = emptySet()
    @Volatile private var loadedConfiguration: String? = null

    /**
     * Domains from "wildcard domain" lists (e.g. the HaGeZi wildcard "onlydomains" lists):
     * each entry blocks the domain and every subdomain. These lists are
     * compressed to the parent domain only, so an exact match would miss
     * almost every real query (ads.example.com vs example.com).
     */
    @Volatile
    private var blockedSuffixes: MutableLongSet = MutableLongSet(0)

    /** Number of entries loaded from wildcard-domain lists. */
    val wildcardDomainCount: Int get() = blockedSuffixes.size
    private val wildcardRules = mutableListOf<BlocklistRule.WildcardDomain>()
    private val regexRules = mutableListOf<BlocklistRule.RegexPattern>()
    private val whitelistedHosts = mutableSetOf<String>()
    private val blockedTlds = mutableSetOf<String>()

    /**
     * True when the enabled lists or the user exceptions differ from the rules
     * currently loaded. The periodic update uses it to avoid reparsing every
     * list when the servers answered "304 Not Modified".
     */
    fun configurationChanged(): Boolean = try {
        loadedConfiguration != configurationSignature()
    } catch (_: Exception) {
        // A concurrent change must never skip a needed reload.
        true
    }

    private fun configurationSignature(): String {
        val builder = StringBuilder()
        config.hosts.items.forEach { item ->
            if (item.state != HostState.IGNORE) {
                builder.append(item.data).append(':').append(item.state.ordinal).append(';')
            }
        }
        builder.append('|')
        config.hosts.exceptions.forEach { exception ->
            builder.append(exception.data).append(':').append(exception.state.ordinal).append(';')
        }
        return builder.toString()
    }

    fun isBlocked(host: String): Boolean {
        val lowerHost = host.lowercase()

        // Check the allow rules first, including parent-domain suffix matches.
        // This makes a single "paypal.com" allow rule cover www.paypal.com,
        // api.paypal.com and so on.
        if (isWhitelisted(lowerHost)) {
            return false
        }

        // Check exact match in blocked hosts
        if (blockedHosts.value.value.contains(hash64(lowerHost))) {
            return true
        }

        // Check wildcard-domain lists: the host itself and each parent domain.
        val suffixes = blockedSuffixes
        if (suffixes.isNotEmpty()) {
            if (suffixes.contains(hash64(lowerHost))) return true
            var dot = lowerHost.indexOf('.')
            while (dot != -1 && dot < lowerHost.length - 1) {
                val parent = lowerHost.substring(dot + 1)
                // Stop before the bare TLD: TLD rules are handled below.
                if (parent.indexOf('.') == -1) break
                if (suffixes.contains(hash64(parent))) return true
                dot = lowerHost.indexOf('.', dot + 1)
            }
        }

        // Check TLD rules (e.g. HaGeZi "Most Abused TLDs")
        val lastDot = lowerHost.lastIndexOf('.')
        val tld = if (lastDot != -1) lowerHost.substring(lastDot + 1) else lowerHost
        if (blockedTlds.contains(tld)) {
            return true
        }

        // Check wildcard rules
        for (rule in wildcardRules) {
            if (rule.regex.matches(lowerHost)) {
                return true
            }
        }

        // Check regex rules
        for (rule in regexRules) {
            if (rule.regex.matches(lowerHost)) {
                return true
            }
        }

        return false
    }

    /**
     * Fast lookup of the list that blocks [host], using the index built by the
     * last [initialize] run. Covers exact domains, wildcard-domain suffixes and
     * TLD rules; returns null for pattern-only rules (rare) and user rules.
     */
    fun sourceFor(host: String): HostSource? {
        val value = host.lowercase().trimEnd('.')
        if (value.isEmpty()) return null
        val hash = value.hashCode()

        lookupSource(exactSources, hash)?.let { listIndex ->
            sourceTitles.getOrNull(listIndex)?.let { return HostSource(it, HostMatchType.EXACT) }
        }
        lookupSource(suffixSources, hash)?.let { listIndex ->
            sourceTitles.getOrNull(listIndex)?.let { return HostSource(it, HostMatchType.EXACT) }
        }

        var dot = value.indexOf('.')
        while (dot != -1 && dot < value.length - 1) {
            val parent = value.substring(dot + 1)
            if (parent.indexOf('.') == -1) break
            lookupSource(suffixSources, parent.hashCode())?.let { listIndex ->
                sourceTitles.getOrNull(listIndex)?.let {
                    return HostSource(it, HostMatchType.PARENT_DOMAIN)
                }
            }
            dot = value.indexOf('.', dot + 1)
        }

        val lastDot = value.lastIndexOf('.')
        val tld = if (lastDot != -1) value.substring(lastDot + 1) else value
        tldSources[tld]?.let { index ->
            sourceTitles.getOrNull(index)?.let { return HostSource(it, HostMatchType.TLD) }
        }
        return null
    }

    /** Binary search in the packed (hash << 32 | listIndex) index. */
    private fun lookupSource(index: LongArray, hash: Int): Int? {
        if (index.isEmpty()) return null
        var low = 0
        var high = index.size
        while (low < high) {
            val mid = (low + high) ushr 1
            val midHash = (index[mid] ushr 32).toInt()
            if (midHash < hash) low = mid + 1 else high = mid
        }
        if (low >= index.size) return null
        val value = index[low]
        if ((value ushr 32).toInt() != hash) return null
        return (value and 0xFFFFFFFFL).toInt()
    }

    /** Pack the list index with its hash for the compact source index. */
    private fun packSource(hash: Int, listIndex: Int): Long =
        (hash.toLong() shl 32) or (listIndex.toLong() and 0xFFFFFFFFL)

    /** Titles of the lists that contain wildcard or regex rules. */
    fun listsWithPatterns(): Set<String> = patternListTitles

    /** Titles of the lists that contain @@ whitelist entries. */
    fun listsWithWhitelistEntries(): Set<String> = whitelistListTitles

    private fun isWhitelisted(host: String): Boolean {
        if (whitelistedHosts.contains(host)) {
            return true
        }

        var dot = host.indexOf('.')
        while (dot != -1 && dot < host.length - 1) {
            if (whitelistedHosts.contains(host.substring(dot + 1))) {
                return true
            }
            dot = host.indexOf('.', dot + 1)
        }
        return false
    }

    @Throws(InterruptedException::class)
    suspend fun initialize(progressCallback: (suspend (Int) -> Unit)? = null) = initMutex.withLock {
        initializeLocked(progressCallback)
    }

    @Throws(InterruptedException::class)
    private suspend fun initializeLocked(progressCallback: (suspend (Int) -> Unit)?) {
        val stats = ArrayList<ListStat>()
        loadingStats = stats
        val signature = configurationSignature()

        val sortedHostItems = config.hosts.items
            .mapNotNull {
                if (it.state != HostState.IGNORE) it else null
            }
            .sortedBy { it.state.ordinal }

        val newHosts = MutableLongSet(sortedHostItems.size + config.hosts.exceptions.size)
        val newSuffixes = MutableLongSet(0)
        loadingSuffixes = newSuffixes

        val newExactSources = MutableLongList()
        val newSuffixSources = MutableLongList()
        val newTldSources = HashMap<String, Int>()
        val titles = ArrayList<String>(sortedHostItems.size)
        loadingPatternLists = HashSet()
        loadingWhitelistLists = HashSet()

        // Clear previous wildcard and regex rules
        wildcardRules.clear()
        regexRules.clear()
        whitelistedHosts.clear()
        blockedTlds.clear()

        val totalItems = sortedHostItems.size
        for ((index, item) in sortedHostItems.withIndex()) {
            if (Thread.interrupted()) {
                throw InterruptedException("Interrupted")
            }
            titles.add(item.title.ifBlank { item.data })
            loadItem(newHosts, item, index, newExactSources, newSuffixSources, newTldSources)

            // Report progress: 75-95% for parsing
            progressCallback?.invoke(75 + ((index + 1) * 20) / totalItems.coerceAtLeast(1))
        }

        for (exception in config.hosts.exceptions) {
            if (Thread.interrupted()) {
                throw InterruptedException("Interrupted")
            }
            addHostException(newHosts, exception)
            if (exception.state == HostState.ALLOW) {
                val value = exception.data.lowercase()
                if (!value.contains('.')) {
                    newTldSources.remove(value)
                }
                // Exact/suffix entries stay in the compact source index: an
                // allowed host is not blocked, so the finder never uses them.
            }
        }

        // Publish the suffixes first: observers of blockedHosts read both.
        blockedSuffixes = newSuffixes
        exactSources = newExactSources.toSortedLongArray()
        suffixSources = newSuffixSources.toSortedLongArray()
        tldSources = newTldSources
        sourceTitles = titles
        patternListTitles = loadingPatternLists
        whitelistListTitles = loadingWhitelistLists
        blockedHosts.value = atomic(newHosts)
        _listStats.value = stats
        val totalRules = newHosts.size + wildcardRules.size + regexRules.size
        Logger.info("Domain rules loaded: ${totalRules + newSuffixes.size} total (${newHosts.size} domains, ${newSuffixes.size} wildcard domains, ${wildcardRules.size} wildcards, ${regexRules.size} regex, ${whitelistedHosts.size} whitelisted)")
        loadedConfiguration = signature
    }

    @Throws(InterruptedException::class)
    private fun loadItem(
        set: MutableLongSet,
        item: HostFile,
        listIndex: Int,
        exactSources: MutableLongList,
        suffixSources: MutableLongList,
        tldSources: MutableMap<String, Int>,
    ) {
        if (item.state == HostState.IGNORE) {
            return
        }

        val reader = try {
            FileHelper.openItemFile(item)
        } catch (e: FileNotFoundException) {
            Logger.warn("${item.title}: File not found, skipping")
            return
        }

        if (reader == null) {
            addHost(set, item, item.data, listIndex, exactSources, suffixSources, tldSources)
        } else {
            loadReader(set, item, reader, listIndex, exactSources, suffixSources, tldSources)
        }
    }

    /** Per-list counts being filled by the current [initialize] run. */
    private var loadingStats: MutableList<ListStat> = ArrayList()

    /** Lists with wildcard/regex patterns in the current [initialize] run. */
    private var loadingPatternLists: MutableSet<String> = HashSet()

    /** Lists with @@ whitelist entries in the current [initialize] run. */
    private var loadingWhitelistLists: MutableSet<String> = HashSet()

    /** Suffix set being filled by the current [initialize] run. */
    private var loadingSuffixes: MutableLongSet = MutableLongSet(0)

    /** Lists in the "wildcard domains" format: an entry also covers its subdomains. */
    private fun Host.isWildcardDomainList(): Boolean = RuleDatabase.isWildcardDomainList(this)

    private fun Host.isTldList(): Boolean = RuleDatabase.isTldList(this)

    private fun addHost(
        set: MutableLongSet,
        item: Host,
        host: String,
        listIndex: Int,
        exactSources: MutableLongList,
        suffixSources: MutableLongList,
        tldSources: MutableMap<String, Int>,
    ) {
        val value = host.lowercase()
        when (item.state) {
            HostState.ALLOW -> {
                if (value.contains('.')) {
                    val hash = hash64(value)
                    set.remove(hash)
                    loadingSuffixes.remove(hash)
                } else {
                    blockedTlds.remove(value)
                    tldSources.remove(value)
                }
                whitelistedHosts.add(value)
            }
            HostState.DENY -> {
                if (value.contains('.')) {
                    if (item.isWildcardDomainList()) {
                        if (loadingSuffixes.add(hash64(value))) {
                            suffixSources.add(packSource(value.hashCode(), listIndex))
                        }
                    } else {
                        if (set.add(hash64(value))) {
                            exactSources.add(packSource(value.hashCode(), listIndex))
                        }
                    }
                } else {
                    blockedTlds.add(value)
                    if (value !in tldSources) tldSources[value] = listIndex
                }
            }
            else -> Unit
        }
    }

    private fun addHostException(set: MutableLongSet, exception: HostException) {
        val domain = exception.data.lowercase()
        when (exception.state) {
            HostState.ALLOW -> {
                if (domain.contains('.')) {
                    val hash = hash64(domain)
                    set.remove(hash)
                    loadingSuffixes.remove(hash)
                } else {
                    blockedTlds.remove(domain)
                }
                whitelistedHosts.add(domain)
            }
            HostState.DENY -> {
                if (domain.contains('.')) {
                    set.add(hash64(domain))
                } else {
                    blockedTlds.add(domain)
                }
            }
            else -> return
        }
    }

    @Throws(InterruptedException::class)
    private fun loadReader(
        set: MutableLongSet,
        item: Host,
        reader: Reader,
        listIndex: Int,
        exactSources: MutableLongList,
        suffixSources: MutableLongList,
        tldSources: MutableMap<String, Int>,
    ) {
        var count = 0
        var wildcardCount = 0
        var regexCount = 0
        var whitelistCount = 0
        val isDeny = item.state == HostState.DENY

        try {
            Logger.debug("Loading rules from ${item.title}")
            BufferedReader(reader, 65536).use { bufferedReader ->
                var line = bufferedReader.readLine()
                while (line != null) {
                    // Check interruption every 1000 lines for better performance
                    if (count % 1000 == 0 && Thread.interrupted()) {
                        throw InterruptedException("Interrupted")
                    }

                    // Use new parser
                    val rule = BlocklistParser.parseLine(line)
                    if (rule != null) {
                        when (rule) {
                            is BlocklistRule.PlainDomain -> {
                                // Single-label entries are TLD rules. Only accept them
                                // from explicit TLD lists to avoid accidental broad blocks
                                // from a malformed hosts file.
                                if (rule.domain.contains('.') || item.isTldList()) {
                                    count++
                                    addHost(set, item, rule.domain, listIndex, exactSources, suffixSources, tldSources)
                                }
                            }
                            is BlocklistRule.WildcardDomain -> {
                                if (isDeny) {
                                    wildcardRules.add(rule)
                                    wildcardCount++
                                }
                            }
                            is BlocklistRule.RegexPattern -> {
                                if (isDeny) {
                                    regexRules.add(rule)
                                    regexCount++
                                }
                            }
                            is BlocklistRule.WhitelistDomain -> {
                                whitelistedHosts.add(rule.domain)
                                whitelistCount++
                            }
                        }
                    }
                    line = bufferedReader.readLine()
                }
            }
            Logger.info("${item.title}: Loaded $count domains, $wildcardCount wildcards, $regexCount regex, $whitelistCount whitelisted")
            if (wildcardCount > 0 || regexCount > 0) {
                loadingPatternLists.add(item.title.ifBlank { item.data })
            }
            if (whitelistCount > 0) {
                loadingWhitelistLists.add(item.title.ifBlank { item.data })
            }
            loadingStats.add(
                ListStat(item.title, item.data, item.state, count + wildcardCount + regexCount + whitelistCount)
            )
        } catch (e: IOException) {
            Logger.error("${item.title}: Failed to read after $count items", e)
        }
    }
}

/**
 * FNV-1a 64-bit hash of a lowercased domain.
 *
 * The blocking sets used to store [String.hashCode] (32 bit), so a collision
 * between a blocked domain and an unrelated one made the latter look blocked.
 * 64 bits make that practically impossible even with millions of rules.
 */
private fun hash64(value: String): Long {
    var hash = -0x340d631b7bdddcdbL
    for (char in value) {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash
}

/** Sorted copy used by the compact source index. */
private fun MutableLongList.toSortedLongArray(): LongArray {
    sort()
    return LongArray(size) { this[it] }
}
