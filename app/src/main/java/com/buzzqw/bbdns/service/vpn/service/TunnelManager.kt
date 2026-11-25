/*
 * Copyright (C) 2025-2026 Vexzure
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
 * You should hinave received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package com.buzzqw.bbdns.service.vpn.service

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import android.os.Build
import android.util.Base64
import android.util.Log
import com.buzzqw.bbdns.BuildConfig
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.service.firewall.handler.RuleHandler
import com.buzzqw.bbdns.service.firewall.handler.filterPacket
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import com.buzzqw.bbdns.service.firewall.utils.DnsFilterExclusions
import com.buzzqw.bbdns.service.vpn.network.transport.tcp.TCPHeader
import com.buzzqw.bbdns.service.vpn.network.transport.udp.UDPModel
import com.buzzqw.bbdns.service.vpn.network.transport.udp.toUDPHeader
import com.buzzqw.bbdns.service.vpn.network.transport.udp.extractUDPData
import com.buzzqw.bbdns.service.vpn.network.transport.udp.handleDnsResponse
import com.buzzqw.bbdns.service.vpn.network.transport.icmp.ICMPModel
import com.buzzqw.bbdns.service.vpn.network.transport.icmp.toICMPPacket
import com.buzzqw.bbdns.service.vpn.network.transport.ipv4.IPv4
import com.buzzqw.bbdns.service.vpn.network.transport.ipv4.toIPv4Header
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DNSModel
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DnsAnswers
import com.buzzqw.bbdns.service.vpn.network.transport.dns.toDNSModel
import com.buzzqw.bbdns.service.vpn.network.util.CnameCloak
import com.buzzqw.bbdns.service.vpn.network.util.CnameCloakGuard
import com.buzzqw.bbdns.service.vpn.network.util.DohCircuitBreaker
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants
import com.buzzqw.bbdns.service.vpn.network.util.SafeSearch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.nio.ByteOrder
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TunnelManager(
    private val ruleHandler: RuleHandler? = null,
    private val dnsServerV4: String = "9.9.9.9",
    private val dnsServerV6: String = "2620:fe::fe",
    /** Secondary IPv4 resolver, used when the primary does not answer. */
    private val dnsServerV4Alt: String = "",
    private val dohUrl: String? = null,
    safeSearchEnabled: Boolean = false,
    localBlockPageEnabled: Boolean = false
) {

    @Volatile
    private var contextPtr: Long = 0
    @Volatile
    private var safeSearchEnabled: Boolean = safeSearchEnabled
    @Volatile
    private var localBlockPage: Boolean = localBlockPageEnabled
    /** Loopback port of the notice page, 0 while the page is off. */
    @Volatile
    private var noticePagePort: Int = 0
    private val dohBreaker = DohCircuitBreaker()
    private val safeSearchCache = ConcurrentHashMap<String, SafeSearchAddress>()
    private val lock = Any()
    @Volatile
    private var isReleased = false
    private val dohScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dohPermits = Semaphore(DOH_MAX_CONCURRENT)
    private val dohCache = object : LinkedHashMap<String, DohCacheEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DohCacheEntry>?): Boolean =
            size > DOH_CACHE_MAX_ENTRIES
    }
    private val dohCacheLock = Any()
    private val secureRandom = SecureRandom()

    private val dohClient = OkHttpClient.Builder()
        .connectTimeout(DOH_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(DOH_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(DOH_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()



    fun initialize(): Boolean {
        contextPtr = jni_init(Build.VERSION.SDK_INT)
        if (contextPtr != 0L) {
            // Set DNS servers in native code
            jni_set_dns_servers(contextPtr, dnsServerV4, dnsServerV4Alt, dnsServerV6)
        }
        return contextPtr != 0L
    }
    
    fun start(logLevel: Int) {
        if (contextPtr != 0L) {
            jni_start(contextPtr, logLevel)
        }
    }
    
    fun run(tunFd: Int, forwardDns: Boolean = true, rcode: Int = 3) {
        if (contextPtr != 0L) {
            // Name the pooled thread while it runs the packet loop, so the
            // battery diagnostics can tell its CPU time from the rest.
            val thread = Thread.currentThread()
            val previousName = thread.name
            thread.name = TUNNEL_THREAD_NAME
            try {
                jni_run(contextPtr, tunFd, forwardDns, rcode)
            } finally {
                thread.name = previousName
            }
        }
    }
    
    fun stop() {
        if (contextPtr != 0L) {
            jni_stop(contextPtr)
        }
    }
    
    fun done() {
        if (contextPtr != 0L) {
            jni_done(contextPtr)
            contextPtr = 0L
        }
    }
    
    fun getMtu(): Int {
        return jni_get_mtu()
    }

    /** Turns SafeSearch DNS redirection on or off without restarting the VPN. */
    fun setSafeSearchEnabled(enabled: Boolean) {
        safeSearchEnabled = enabled
    }

    /**
     * Turns the local notice page on or off and tells the native stack which
     * loopback port to relay a blocked site to. Must be called after
     * [initialize]: there is no tunnel to point anywhere before that.
     */
    fun setLocalBlockPage(enabled: Boolean, port: Int) {
        localBlockPage = enabled && port > 0
        noticePagePort = if (localBlockPage) port else 0
        if (contextPtr != 0L) {
            try {
                jni_set_notice_page(
                    contextPtr,
                    if (localBlockPage) NetworkConstants.NOTICE_PAGE_ADDRESS else "",
                    noticePagePort
                )
            } catch (e: UnsatisfiedLinkError) {
                Logger.error("Native library unavailable for the notice page: ${e.message}")
            }
        }
    }
    
    fun getProperty(name: String): String {
        return jni_getprop(name)
    }
    
    fun setDnsServers(dnsV4: String, dnsV6: String) {
        if (contextPtr != 0L) {
            jni_set_dns_servers(contextPtr, dnsV4, dnsServerV4Alt, dnsV6)
        }
    }
    
    fun clearSessions() {
        synchronized(lock) {
            try {
                if (isReleased) {
                    Logger.warn("Cannot clear sessions: TunnelManager has been released")
                    return
                }
                val contextPtrSnapshot = contextPtr
                if (contextPtrSnapshot != 0L) {
                    jni_clear_sessions(contextPtrSnapshot)
                } else {
                    Logger.warn("Cannot clear sessions: TunnelManager not properly initialized")
                }
            } catch (e: UnsatisfiedLinkError) {
                Logger.error("Native library unavailable for clearSessions: ${e.message}")
            } catch (e: Exception) {
                Logger.error("Error in clearSessions: ${e.message}", e)
            }
        }
    }

    private fun onTcpPacketReceived(data: ByteArray, length: Int, direction: String): Boolean {
        return try {
            val buffer = ByteBuffer.wrap(data, 0, length)
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            // Check IP version - first 4 bits
            val firstByte = buffer.get(0).toInt() and 0xFF
            val ipVersion = (firstByte shr 4) and 0xF
            
            if (ipVersion == 6) {
                // IPv6 packets - currently not supported for TCP filtering
                return true
            } else if (ipVersion != 4) {
                Log.w("PacketFilter", "[$direction] Unknown IP version $ipVersion, blocking")
                return false
            }
            
            val ipHeader = buffer.toIPv4Header()
            val tcpHeader = TCPHeader.fromByteBuffer(buffer.slice())

            // Use existing firewall filtering logic
            ruleHandler?.let { ruleHandler ->
                val isSynPacket = tcpHeader.flags.contains(com.buzzqw.bbdns.service.vpn.network.transport.tcp.TCPFlag.SYN) && 
                                 !tcpHeader.flags.contains(com.buzzqw.bbdns.service.vpn.network.transport.tcp.TCPFlag.ACK)
                val filterResult = filterPacket(tcpHeader, ipHeader, ruleHandler, bypassCheck = !isSynPacket)
                
                val allowed = filterResult.first
                
                allowed
            } ?: run {
                Log.d("PacketFilter", "[$direction] TCP: No RuleHandler, allowing packet")
                true // Allow if no rule handler
            }
        } catch (e: Exception) {
            // For malformed packets (like Data Offset 0), block them for security
            if (e.message?.contains("Malformed TCP packet") == true) {
                if (BuildConfig.DEBUG) {
                    val packetHex = data.take(minOf(length, 100)).joinToString(" ") { "%02x".format(it) }
                    Log.d("PacketFilter", "[$direction] TCP: Blocking malformed packet: ${e.message}")
                    Log.d("PacketFilter", "[$direction] TCP: Full packet hex (first 100 bytes): $packetHex")
                }
                false // Block malformed packets
            } else {
                Log.e("PacketFilter", "Error filtering TCP packet: ${e.message}")
                true // Allow packet if other parsing errors occur
            }
        }
    }

    private fun onUdpPacketReceived(data: ByteArray, length: Int, direction: String): Boolean {
        return try {
            val buffer = ByteBuffer.wrap(data, 0, length)
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            val ipHeader = buffer.toIPv4Header()
            val udpStartPosition = buffer.position()
            val udpHeaderBuffer = buffer.slice()
            val udpHeader = udpHeaderBuffer.toUDPHeader()
            
            // Use existing firewall filtering logic
            ruleHandler?.let { ruleHandler ->
                val isDnsPacket = udpHeader.destinationPort == 53
                val rawDnsQuery: ByteArray? = if (isDnsPacket) {
                    try {
                        udpHeader.extractUDPData(udpHeaderBuffer)
                    } catch (e: Exception) {
                        Log.w("PacketFilter", "Failed to extract DNS payload: ${e.message}")
                        null
                    }
                } else null
                val dnsModel: DNSModel? = rawDnsQuery?.let {
                    try {
                        ByteBuffer.wrap(it).toDNSModel()
                    } catch (e: Exception) {
                        Log.w("PacketFilter", "Failed to parse DNS model: ${e.message}")
                        null
                    }
                }

                val filterResult = filterPacket(udpHeader, ipHeader, ruleHandler, dnsModel)
                val allowed = filterResult.first
                val firewallResult = filterResult.third
                val safeSearchTarget = if (isDnsPacket && rawDnsQuery != null && dnsModel != null &&
                    safeSearchEnabled && isDnsQuery(rawDnsQuery) &&
                    !DnsFilterExclusions.isExcluded(filterResult.second)
                ) {
                    SafeSearch.targetFor(dnsModel.domainName, dnsModel.queryType)
                } else null
                
                // Handle DNS blocking with a reply built on the phone
                if (isDnsPacket && firewallResult == FirewallResult.DNS_BLOCKED) {
                    try {
                        // Create a buffer with UDP header and data for the reply
                        val udpPacketData = ByteBuffer.wrap(data, udpStartPosition, length - udpStartPosition)
                        udpPacketData.order(ByteOrder.BIG_ENDIAN)
                        val wire = rawDnsQuery ?: udpHeader.extractUDPData(udpPacketData)

                        // With the notice page on, an A query is answered with
                        // the address the native stack relays to the local
                        // server, so the browser gets an explanation. Every
                        // other query keeps the plain blocked answer.
                        val responsePayload = if (localBlockPage && noticePagePort > 0) {
                            DnsAnswers.noticeReply(wire, NetworkConstants.NOTICE_PAGE_ADDRESS)
                        } else {
                            DnsAnswers.blockedReply(wire)
                        }

                        // Create complete response packet using Kotlin packet creation system
                        val completeResponsePacket = handleDnsResponse(udpHeader, ipHeader, responsePayload)

                        // Send complete packet through VPN interface
                        if (contextPtr != 0L) {
                            jni_send_complete_packet(contextPtr, completeResponsePacket)
                        }
                        false // Block original packet
                    } catch (e: Exception) {
                        Log.e("PacketFilter", "Error creating/sending DNS reply: ${e.message}", e)
                        false // Block packet on error
                    }
                } else if (isDnsPacket && rawDnsQuery != null && dnsModel != null && safeSearchTarget != null) {
                    // Answer with the SafeSearch address instead of forwarding the
                    // query. Other record types and failures are handled by
                    // scheduleSafeSearchResponse, which falls back to a normal
                    // resolution so search keeps working.
                    scheduleSafeSearchResponse(
                        udpHeader,
                        ipHeader,
                        rawDnsQuery,
                        safeSearchTarget,
                        dnsModel.queryType
                    )
                    false
                } else if (isDnsPacket && rawDnsQuery != null && dohUrl != null && isDnsQuery(rawDnsQuery)) {
                    // Resolve the query over HTTPS and inject the response into the TUN.
                    // The original UDP packet is blocked so it never reaches plain DNS.
                    scheduleDohResponse(udpHeader, ipHeader, rawDnsQuery, filterResult.second)
                    false
                } else {
                    allowed
                }
            } ?: run {
                true
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun onIcmpPacketReceived(data: ByteArray, length: Int, direction: String): Boolean {
        return try {
            val buffer = ByteBuffer.wrap(data, 0, length)
            buffer.order(ByteOrder.BIG_ENDIAN)
            
            val ipHeader = buffer.toIPv4Header()
            val icmpPacket = buffer.slice().toICMPPacket()

            ruleHandler?.let { ruleHandler ->
                val filterResult = filterPacket(icmpPacket, ipHeader, ruleHandler)
                
                val allowed = filterResult.first

                allowed
            } ?: run {
                Log.d("PacketFilter", "[$direction] ICMP: No RuleHandler, allowing packet")
                true
            }
        } catch (e: Exception) {
            Log.e("PacketFilter", "Error filtering ICMP packet: ${e.message}")
            true
        }
    }

    private fun isDnsQuery(query: ByteArray): Boolean =
        query.size >= 12 && (query[2].toInt() and 0x80) == 0

    private fun scheduleDohResponse(udpHeader: UDPModel, ipHeader: IPv4, query: ByteArray, uid: Int) {
        val endpoint = dohUrl ?: return
        if (contextPtr == 0L) return

        dohScope.launch {
            // Bound the number of blocking lookups so a slow or unreachable
            // DoH server cannot exhaust the shared IO pool. A blocked alias is
            // journaled here: the query itself was allowed, so the packet path
            // has no log line for it.
            val response = dohPermits.withPermit {
                resolveDns(endpoint, query, uid = uid, onCnameBlock = { cloak ->
                    ruleHandler?.recordCnameBlock(uid, udpHeader, ipHeader, cloak.domain, cloak.target)
                })
            }
            try {
                val packet = handleDnsResponse(udpHeader, ipHeader, response)
                // Inject under the release lock: the native context must not
                // be stopped/freed while we are writing into the TUN.
                synchronized(lock) {
                    if (!isReleased && contextPtr != 0L) {
                        jni_send_complete_packet(contextPtr, packet)
                    }
                }
            } catch (e: Exception) {
                Logger.error("DoH: failed to inject response: ${e.message}", e)
            }
        }
    }

    private fun scheduleSafeSearchResponse(
        udpHeader: UDPModel,
        ipHeader: IPv4,
        query: ByteArray,
        target: String,
        queryType: Int
    ) {
        if (contextPtr == 0L) return
        dohScope.launch {
            val payload = try {
                val address = resolveSafeSearchAddress(target, queryType)
                address?.let { SafeSearch.buildResponse(query, it) }
            } catch (e: Exception) {
                Logger.warn("SafeSearch: ${target} resolution failed: ${e.message}")
                null
            } ?: forwardQuery(query)

            try {
                val packet = handleDnsResponse(udpHeader, ipHeader, payload)
                synchronized(lock) {
                    if (!isReleased && contextPtr != 0L) {
                        jni_send_complete_packet(contextPtr, packet)
                    }
                }
            } catch (e: Exception) {
                Logger.error("SafeSearch: failed to inject response: ${e.message}", e)
            }
        }
    }

    private fun resolveSafeSearchAddress(target: String, queryType: Int): InetAddress? {
        val type = if (queryType == Type.AAAA) Type.AAAA else Type.A
        val key = "$target/$type"
        val now = System.currentTimeMillis()
        safeSearchCache[key]?.let { entry ->
            if (entry.expiresAt > now) return entry.address
        }

        val request = Message()
        // nextInt(bound) keeps the call on java.util.Random, available since
        // API 24; the two-argument overload lives on RandomGenerator (API 35).
        request.header.setID(1 + secureRandom.nextInt(65534))
        request.header.setFlag(Flags.RD.toInt())
        request.addRecord(
            Record.newRecord(Name.fromString("$target."), type, DClass.IN),
            Section.QUESTION
        )
        val response = Message(forwardQuery(request.toWire()))
        val answer = response.getSection(Section.ANSWER).firstOrNull { record ->
            (type == Type.A && record is ARecord) || (type == Type.AAAA && record is AAAARecord)
        } ?: return null
        val address = when (answer) {
            is ARecord -> answer.address
            is AAAARecord -> answer.address
            else -> return null
        }
        safeSearchCache[key] = SafeSearchAddress(address, now + SAFE_SEARCH_CACHE_MS)
        return address
    }

    /**
     * Resolves [query] the same way the normal path would, so an unexpected
     * SafeSearch failure never leaves the client without an answer.
     */
    private fun forwardQuery(query: ByteArray): ByteArray {
        val endpoint = dohUrl
        if (endpoint != null) return resolveDns(endpoint, query)
        return try {
            fetchPlainDns(query)
        } catch (e: Exception) {
            Logger.warn("SafeSearch: plain DNS fallback failed: ${e.message}")
            servFail(query)
        }
    }

    private fun resolveDns(
        endpoint: String,
        query: ByteArray,
        uid: Int = -1,
        onCnameBlock: ((CnameCloak) -> Unit)? = null
    ): ByteArray {
        // An excluded app must never receive an answer that was filtered for
        // somebody else, or leave a filtered answer in the shared cache.
        val unfiltered = uid > 0 && DnsFilterExclusions.isExcluded(uid)
        val key = dohCacheKey(query)
        if (!unfiltered) {
            synchronized(dohCacheLock) {
                dohCache[key]?.let { entry ->
                    if (entry.expiresAt > System.currentTimeMillis()) {
                        dohCacheHits.incrementAndGet()
                        return withTransactionId(entry.response, query)
                    }
                    dohCache.remove(key)
                }
            }
        }

        dohRequests.incrementAndGet()
        val response = try {
            if (!dohBreaker.allowRequest()) {
                // The endpoint has been failing: skip the timeout and ask the
                // plain resolver straight away.
                Logger.warn("DoH: paused after ${dohBreaker.failureCount()} failures; using plain DNS")
                fetchPlainDns(query)
            } else {
                try {
                    fetchDohWithRetry(endpoint, query).also { dohBreaker.recordSuccess() }
                } catch (e: Exception) {
                    dohBreaker.recordFailure()
                    throw e
                }
            }
        } catch (e: Exception) {
            dohFailures.incrementAndGet()
            Logger.warn("DoH: ${e.message}; falling back to plain DNS")
            try {
                fetchPlainDns(query)
            } catch (fallback: Exception) {
                Logger.warn("DoH/plain DNS fallback failed: ${fallback.message}")
                servFail(query)
            }
        }

        val normalized = if (response.size >= 12) response else servFail(query)
        // A blocked alias is detected on the answer, before it is cached, so
        // the cached bytes are always the upstream ones.
        val guarded = applyCnameGuard(normalized, query, uid, onCnameBlock)
        val cached = guarded.copyOf().also {
            it[0] = 0
            it[1] = 0
        }
        val truncated = (guarded[2].toInt() and 0x02) != 0
        val servFailResponse = (guarded[3].toInt() and 0x0F) == 2
        if (!unfiltered && guarded === normalized && !truncated && !servFailResponse) {
            synchronized(dohCacheLock) {
                dohCache[key] = DohCacheEntry(
                    expiresAt = System.currentTimeMillis() + DOH_CACHE_TTL_MS,
                    response = cached
                )
            }
        }
        return withTransactionId(cached, query)
    }

    /**
     * Blocks an answer whose CNAME chain ends in a blocked domain, even when
     * the queried name itself is clean (CNAME cloaking). The reply is built
     * here so the browser fails fast instead of resolving the tracker.
     */
    private fun applyCnameGuard(
        response: ByteArray,
        query: ByteArray,
        uid: Int,
        onCnameBlock: ((CnameCloak) -> Unit)?
    ): ByteArray {
        val handler = ruleHandler ?: return response
        val queried = try {
            Message(query).question?.name?.toString(true)
        } catch (e: Exception) {
            null
        } ?: return response

        val cloak = CnameCloakGuard.find(
            response = response,
            queried = queried,
            isBlocked = handler::isDomainBlocked,
            isExplicitlyAllowed = handler::isDomainExplicitlyAllowed,
            uid = uid,
            isExcluded = DnsFilterExclusions::isExcluded
        ) ?: return response

        onCnameBlock?.invoke(cloak)
        // No notice page here: the page names the list that blocks the site,
        // and this site is not on a list. It is blocked because of where it
        // points, which the page would explain wrongly.
        return DnsAnswers.blockedReply(query)
    }

    /**
     * POST the DNS message to the DoH endpoint. A shared OkHttp client keeps
     * connections alive and multiplexes them over HTTP/2, so most queries do
     * not pay for a new TLS handshake.
     */
    private fun fetchDoh(endpoint: String, query: ByteArray): ByteArray {
        val request = Request.Builder()
            .url(endpoint)
            .post(query.toRequestBody(DNS_MEDIA_TYPE))
            .header("Accept", "application/dns-message")
            .header("User-Agent", "BBDNS/1.0")
            .build()
        dohClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from $endpoint")
            }
            val body = response.body?.bytes() ?: throw IOException("Empty DoH response")
            if (body.size < 12) throw IOException("Short DoH response")
            return body
        }
    }

    /**
     * Retry once when the first DoH attempt failed immediately (refused
     * connection, DNS error). A timeout already used the whole budget, so it
     * goes straight to the plain-DNS fallback instead of doubling the delay.
     */
    private fun fetchDohWithRetry(endpoint: String, query: ByteArray): ByteArray {
        val startedAt = System.currentTimeMillis()
        return try {
            fetchDoh(endpoint, query)
        } catch (e: IOException) {
            if (System.currentTimeMillis() - startedAt > DOH_FAST_RETRY_MS) throw e
            Logger.warn("DoH: ${e.message}; retrying once")
            fetchDoh(endpoint, query)
        }
    }

    private fun fetchPlainDns(query: ByteArray): ByteArray {
        val servers = listOf(dnsServerV4, dnsServerV4Alt)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (servers.isEmpty()) throw IOException("No fallback DNS server")

        var lastError: Exception? = null
        for (server in servers) {
            try {
                return queryPlainDns(server, query)
            } catch (e: Exception) {
                lastError = e
                Logger.warn("Plain DNS $server failed: ${e.message}")
            }
        }
        throw lastError ?: IOException("No fallback DNS server")
    }

    private fun queryPlainDns(server: String, query: ByteArray): ByteArray {
        DatagramSocket().use { socket ->
            socket.soTimeout = PLAIN_DNS_TIMEOUT_MS
            val address = InetAddress.getByName(server)
            socket.send(DatagramPacket(query, query.size, address, 53))
            val buffer = ByteArray(4096)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            return response.data.copyOf(response.length)
        }
    }

    private fun servFail(query: ByteArray): ByteArray = DnsAnswers.servFail(query)

    private fun dohCacheKey(query: ByteArray): String {
        val normalized = query.copyOf()
        if (normalized.size >= 2) {
            normalized[0] = 0
            normalized[1] = 0
        }
        return Base64.encodeToString(normalized, Base64.NO_WRAP)
    }

    private fun withTransactionId(response: ByteArray, query: ByteArray): ByteArray {
        val copy = response.copyOf()
        if (copy.size >= 2 && query.size >= 2) {
            copy[0] = query[0]
            copy[1] = query[1]
        }
        return copy
    }

    private data class DohCacheEntry(val expiresAt: Long, val response: ByteArray)

    private data class SafeSearchAddress(val address: InetAddress, val expiresAt: Long)

    fun release() {
        synchronized(lock) {
            if (!isReleased) {
                isReleased = true
                dohScope.cancel()
                val contextPtrSnapshot = contextPtr
                if (contextPtrSnapshot != 0L) {
                    try {
                        stop()
                        done()
                    } catch (e: UnsatisfiedLinkError) {
                        Logger.error("Native library unavailable during release: ${e.message}")
                    } catch (e: Exception) {
                        Logger.error("Error during TunnelManager release: ${e.message}", e)
                    } finally {
                        contextPtr = 0L
                    }
                    Logger.info("TunnelManager resources released")
                } else {
                    Logger.info("TunnelManager was already released or not initialized")
                }
            }
        }
    }


    private external fun jni_init(sdk: Int): Long
    private external fun jni_start(context: Long, loglevel: Int)
    private external fun jni_run(context: Long, tun: Int, fwd53: Boolean, rcode: Int)
    private external fun jni_stop(context: Long)
    private external fun jni_done(context: Long)
    private external fun jni_getprop(name: String): String
    private external fun jni_get_mtu(): Int
    private external fun jni_clear_sessions(context: Long)
    private external fun jni_set_dns_servers(context: Long, dnsV4: String, dnsV4Alt: String, dnsV6: String)
    private external fun jni_set_notice_page(context: Long, address: String, port: Int)
    private external fun jni_send_complete_packet(context: Long, packetData: ByteArray)

    companion object {
        const val TUNNEL_THREAD_NAME = "bbdns-tunnel"

        /** DoH lookups that went to the network, cache hits and failures. */
        val dohRequests = AtomicLong(0)
        val dohCacheHits = AtomicLong(0)
        val dohFailures = AtomicLong(0)

        @JvmStatic
        external fun jni_get_stats(): LongArray?

        /** Native event-loop counters (see STAT_* in bbdns.h), or null. */
        fun nativeStats(): LongArray? = try {
            jni_get_stats()
        } catch (e: UnsatisfiedLinkError) {
            null
        }

        private const val DOH_CONNECT_TIMEOUT_MS = 1_500L
        private const val DOH_READ_TIMEOUT_MS = 2_500L
        private const val DOH_CALL_TIMEOUT_MS = 3_000L
        private const val DOH_FAST_RETRY_MS = 1_000L
        private const val DOH_MAX_CONCURRENT = 8
        private const val PLAIN_DNS_TIMEOUT_MS = 1_500
        private const val DOH_CACHE_TTL_MS = 60_000L
        private const val DOH_CACHE_MAX_ENTRIES = 256
        /** SafeSearch targets change rarely: five minutes avoids a lookup per query. */
        private const val SAFE_SEARCH_CACHE_MS = 300_000L
        private val DNS_MEDIA_TYPE = "application/dns-message".toMediaType()

        init {
            System.loadLibrary("bbdns")
        }
    }
}