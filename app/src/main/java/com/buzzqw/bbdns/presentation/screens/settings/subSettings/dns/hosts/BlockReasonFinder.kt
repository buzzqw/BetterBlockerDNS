/*
 * Copyright (C) 2026 buzzqw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts

import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.domain.model.CustomDomain
import com.buzzqw.bbdns.domain.repository.CustomDomainRepository
import com.buzzqw.bbdns.presentation.config
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase.Companion.HostMatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** How a rule matched the queried domain. */
enum class BlockMatchType {
    /** The rule names exactly the queried domain. */
    EXACT,

    /** The rule names a parent domain and covers its subdomains. */
    PARENT_DOMAIN,

    /** The rule blocks or allows a whole top-level domain. */
    TLD,

    /** A wildcard pattern such as `*.example.com`. */
    WILDCARD,

    /** A regular expression rule. */
    REGEX,
}

/** Where a matching rule comes from. */
enum class BlockRuleSource {
    /** A downloaded or local blocklist / allowlist from config.hosts.items. */
    LIST,

    /** A user rule from config.hosts.exceptions. */
    USER_RULE,

    /** A rule from the in-app custom domain management screen. */
    CUSTOM_DOMAIN,
}

/**
 * One rule that matched the queried domain.
 *
 * @param sourceName list title, user rule title or custom rule description.
 * @param rule the rule text as written in the source (domain, TLD or pattern).
 */
data class BlockMatch(
    val source: BlockRuleSource,
    val sourceName: String,
    val rule: String,
    val type: BlockMatchType,
)

/**
 * Result of a [BlockReasonFinder.find] call.
 *
 * @param blockedBy rules that block the domain.
 * @param allowedBy rules that allow the domain (they win over list blocks).
 * @param isCurrentlyBlocked whether the current configuration blocks the domain.
 * @param unreadableLists titles of active lists whose cached file is missing.
 */
data class BlockReason(
    val domain: String,
    val blockedBy: List<BlockMatch>,
    val allowedBy: List<BlockMatch>,
    val isCurrentlyBlocked: Boolean,
    val unreadableLists: List<String>,
)

/**
 * Explains why a domain is blocked. [RuleDatabase] only keeps hashes, so this
 * scans the cached list files on demand, using the same parser and the same
 * matching semantics as [RuleDatabase.isBlocked] and the custom domain rule.
 */
class BlockReasonFinder @Inject constructor(
    private val ruleDatabase: RuleDatabase,
    private val customDomainRepository: CustomDomainRepository,
) {

    /**
     * Scans user rules, custom domain rules and every active list for [domain].
     * Runs on [Dispatchers.IO] and is cancellable. Each list stops at its first
     * blocking match. [onProgress] receives (lists scanned, total lists).
     */
    suspend fun find(
        domain: String,
        onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> },
    ): BlockReason = withContext(Dispatchers.IO) {
        val host = normalize(domain)
        // The fast source index lives in RuleDatabase. Make sure the lists have
        // been parsed even when the user opens the log before starting a VPN.
        if (config.hosts.items.any { it.state != HostState.IGNORE } &&
            ruleDatabase.blockedHosts.value.value.isEmpty()
        ) {
            ruleDatabase.initialize()
        }
        val blockedBy = mutableListOf<BlockMatch>()
        val allowedBy = mutableListOf<BlockMatch>()
        val unreadable = mutableListOf<String>()

        // In-app custom domain rules: blocks are checked before allows and both
        // override the lists (see CustomDomainRule).
        val customBlocks = matchCustomDomains(host, allowlist = false)
        val customAllows = matchCustomDomains(host, allowlist = true)

        // User rules from config.hosts.exceptions.
        for (exception in config.hosts.exceptions.toList()) {
            val value = normalize(exception.data)
            if (value.isEmpty()) continue
            val name = exception.title.ifBlank { value }
            when (exception.state) {
                HostState.ALLOW -> allowType(host, value)?.let {
                    allowedBy += BlockMatch(BlockRuleSource.USER_RULE, name, value, it)
                }
                HostState.DENY -> exactOrTldType(host, value)?.let {
                    blockedBy += BlockMatch(BlockRuleSource.USER_RULE, name, value, it)
                }
                else -> Unit
            }
        }

        val lists = config.hosts.items.filter { it.state != HostState.IGNORE }
        val registryBlocked = ruleDatabase.isBlocked(host)
        val isBlocked = when {
            customBlocks.isNotEmpty() -> true
            customAllows.isNotEmpty() -> false
            allowedBy.isNotEmpty() -> false
            blockedBy.isNotEmpty() -> true
            else -> registryBlocked
        }

        // Fast path: the in-memory source index already knows which list blocks
        // the domain. Only fall back to scanning files for pattern-only rules.
        if (isBlocked && customBlocks.isEmpty() && blockedBy.isEmpty()) {
            ruleDatabase.sourceFor(host)?.let { source ->
                blockedBy += BlockMatch(
                    BlockRuleSource.LIST,
                    source.title,
                    host,
                    source.type.toBlockMatchType(),
                )
            }
        }

        val scanTargets: List<HostFile> = when {
            // Not blocked: only allow lists (or lists with @@ entries) can
            // explain the decision.
            !isBlocked && allowedBy.isEmpty() -> {
                val whitelistTitles = ruleDatabase.listsWithWhitelistEntries()
                lists.filter {
                    it.state == HostState.ALLOW ||
                        it.title.ifBlank { it.data } in whitelistTitles
                }
            }
            // Blocked but no source yet: only lists with wildcard/regex rules.
            isBlocked && customBlocks.isEmpty() && blockedBy.isEmpty() -> {
                val patternTitles = ruleDatabase.listsWithPatterns()
                if (patternTitles.isEmpty()) {
                    emptyList()
                } else {
                    lists.filter {
                        it.state == HostState.DENY &&
                            it.title.ifBlank { it.data } in patternTitles
                    }
                }
            }
            else -> emptyList()
        }

        onProgress(0, scanTargets.size)
        for ((index, item) in scanTargets.withIndex()) {
            coroutineContext.ensureActive()
            if (!scanList(item, host, blockedBy, allowedBy)) {
                unreadable += item.title.ifBlank { item.data }
            }
            onProgress(index + 1, scanTargets.size)
        }

        BlockReason(
            domain = host,
            blockedBy = customBlocks + blockedBy,
            allowedBy = customAllows + allowedBy,
            isCurrentlyBlocked = isBlocked,
            unreadableLists = unreadable,
        )
    }

    private suspend fun matchCustomDomains(host: String, allowlist: Boolean): List<BlockMatch> {
        val domains: List<CustomDomain> = try {
            if (allowlist) {
                customDomainRepository.getEnabledAllowlistDomains().first()
            } else {
                customDomainRepository.getEnabledBlocklistDomains().first()
            }
        } catch (e: Exception) {
            Logger.warn("BlockReasonFinder: cannot read custom domains: ${e.message}")
            emptyList()
        }
        return domains.mapNotNull { custom ->
            val type = if (custom.isRegex) {
                val matches = try {
                    Regex(custom.domain, RegexOption.IGNORE_CASE).matches(host)
                } catch (e: Exception) {
                    false
                }
                if (matches) BlockMatchType.REGEX else null
            } else {
                val value = normalize(custom.domain)
                when {
                    host == value -> BlockMatchType.EXACT
                    host.endsWith(".$value") -> BlockMatchType.PARENT_DOMAIN
                    else -> null
                }
            }
            type?.let {
                BlockMatch(
                    source = BlockRuleSource.CUSTOM_DOMAIN,
                    sourceName = custom.description.ifBlank { custom.domain },
                    rule = custom.domain,
                    type = it,
                )
            }
        }
    }

    /**
     * Scans one list. Returns false when its file cannot be opened.
     * Mirrors RuleDatabase.loadReader / addHost.
     */
    private suspend fun scanList(
        item: HostFile,
        host: String,
        blockedBy: MutableList<BlockMatch>,
        allowedBy: MutableList<BlockMatch>,
    ): Boolean {
        val title = item.title.ifBlank { item.data }
        val isDeny = item.state == HostState.DENY
        val isWildcardList = RuleDatabase.isWildcardDomainList(item)
        val isTldList = RuleDatabase.isTldList(item)

        fun onPlain(value: String): BlockMatch? {
            if (!value.contains('.') && !isTldList) return null
            val type = when {
                !isDeny -> allowType(host, value)
                isWildcardList && value.contains('.') -> denySuffixType(host, value)
                else -> exactOrTldType(host, value)
            } ?: return null
            return BlockMatch(BlockRuleSource.LIST, title, value, type)
        }

        val reader = try {
            FileHelper.openItemFile(item)
        } catch (e: FileNotFoundException) {
            return false
        } catch (e: IOException) {
            return false
        }

        // Inline entry (not a file): the location itself is the rule.
        if (reader == null) {
            val match = onPlain(normalize(item.data)) ?: return true
            if (isDeny) {
                blockedBy += match
            } else {
                allowedBy += match
            }
            return true
        }

        // Every candidate except the bare TLD contains the last two labels:
        // lines without them can only matter if they are patterns.
        val needle = lastTwoLabels(host)
        try {
            BufferedReader(reader, 65536).use { bufferedReader ->
                var count = 0
                var line = bufferedReader.readLine()
                while (line != null) {
                    if (++count % 4096 == 0) coroutineContext.ensureActive()
                    if (isTldList || line.contains(needle, ignoreCase = true) || mayBePattern(line)) {
                        val rule = BlocklistParser.parseLine(line)
                        val match = when (rule) {
                            is BlocklistRule.PlainDomain -> onPlain(rule.domain)
                            is BlocklistRule.WhitelistDomain ->
                                allowType(host, rule.domain)?.let {
                                    BlockMatch(BlockRuleSource.LIST, title, rule.domain, it)
                                }
                            is BlocklistRule.WildcardDomain ->
                                if (isDeny && rule.regex.matches(host)) {
                                    BlockMatch(BlockRuleSource.LIST, title, rule.pattern, BlockMatchType.WILDCARD)
                                } else null
                            is BlocklistRule.RegexPattern ->
                                if (isDeny && rule.regex.matches(host)) {
                                    BlockMatch(BlockRuleSource.LIST, title, rule.pattern, BlockMatchType.REGEX)
                                } else null
                            null -> null
                        }
                        if (match != null) {
                            if (!isDeny || rule is BlocklistRule.WhitelistDomain) {
                                if (match !in allowedBy) allowedBy += match
                                // An allow list only needs one hit.
                                if (!isDeny) return true
                            } else {
                                blockedBy += match
                                return true
                            }
                        }
                    }
                    line = bufferedReader.readLine()
                }
            }
        } catch (e: IOException) {
            Logger.warn("BlockReasonFinder: failed to read $title: ${e.message}")
            return false
        }
        return true
    }

    private fun normalize(value: String): String = value.trim().trimEnd('.').lowercase()

    private fun HostMatchType.toBlockMatchType(): BlockMatchType = when (this) {
        HostMatchType.EXACT -> BlockMatchType.EXACT
        HostMatchType.PARENT_DOMAIN -> BlockMatchType.PARENT_DOMAIN
        HostMatchType.TLD -> BlockMatchType.TLD
    }

    private fun lastTwoLabels(host: String): String {
        val last = host.lastIndexOf('.')
        if (last <= 0) return host
        val previous = host.lastIndexOf('.', last - 1)
        return if (previous == -1) host else host.substring(previous + 1)
    }

    /** Lines that the parser may turn into wildcard or regex rules. */
    private fun mayBePattern(line: String): Boolean {
        if (line.isEmpty()) return false
        val first = line[0]
        if (first == '#' || first == '!') return false
        if (line.contains('*') || first == '/') return true
        // Plain ABP domain rules are matched through the needle.
        if (line.startsWith("||") || line.startsWith("@@||")) return false
        return line.any { it in PATTERN_CHARS }
    }

    /** Allow semantics of RuleDatabase.isWhitelisted: exact or any parent. */
    private fun allowType(host: String, value: String): BlockMatchType? = when {
        host == value -> if (value.contains('.')) BlockMatchType.EXACT else BlockMatchType.TLD
        host.endsWith(".$value") ->
            if (value.contains('.')) BlockMatchType.PARENT_DOMAIN else BlockMatchType.TLD
        else -> null
    }

    /** Deny semantics of a normal list or user rule: exact domain or TLD. */
    private fun exactOrTldType(host: String, value: String): BlockMatchType? = when {
        value.contains('.') -> if (host == value) BlockMatchType.EXACT else null
        host.substringAfterLast('.') == value -> BlockMatchType.TLD
        else -> null
    }

    /** Deny semantics of a wildcard-domain list: the domain or any parent. */
    private fun denySuffixType(host: String, value: String): BlockMatchType? = when {
        host == value -> BlockMatchType.EXACT
        host.endsWith(".$value") -> BlockMatchType.PARENT_DOMAIN
        else -> null
    }

    private companion object {
        const val PATTERN_CHARS = "^$[]()\\+?|"
    }
}
