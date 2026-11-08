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
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.service.firewall.handler.RuleHandler
import com.buzzqw.bbdns.service.firewall.handler.filterPacket
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
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
import com.buzzqw.bbdns.service.vpn.network.transport.dns.toDNSModel
import com.buzzqw.bbdns.service.vpn.network.transport.udp.soarResponse
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TunnelManager(
    private val ruleHandler: RuleHandler? = null,
    private val dnsServerV4: String = "9.9.9.9",
    private val dnsServerV6: String = "2620:fe::fe",
    private val dohUrl: String? = null
) {

    @Volatile
    private var contextPtr: Long = 0
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



    fun initialize(): Boolean {
        contextPtr = jni_init(Build.VERSION.SDK_INT)
        if (contextPtr != 0L) {
            // Set DNS servers in native code
            jni_set_dns_servers(contextPtr, dnsServerV4, dnsServerV6)
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
            jni_run(contextPtr, tunFd, forwardDns, rcode)
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
    
    fun getProperty(name: String): String {
        return jni_getprop(name)
    }
    
    fun setDnsServers(dnsV4: String, dnsV6: String) {
        if (contextPtr != 0L) {
            jni_set_dns_servers(contextPtr, dnsV4, dnsV6)
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
                Log.d("PacketFilter", "[$direction] IPv6 TCP packet detected, allowing (not implemented)")
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
                // Log full packet data for debugging
                val packetHex = data.take(minOf(length, 100)).joinToString(" ") { "%02x".format(it) }
                Log.d("PacketFilter", "[$direction] TCP: Blocking malformed packet: ${e.message}")
                Log.d("PacketFilter", "[$direction] TCP: Full packet hex (first 100 bytes): $packetHex")
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
                
                // Handle DNS blocking with SOAR response
                if (isDnsPacket && firewallResult == FirewallResult.DNS_BLOCKED) {
                    try {
                        // Create a buffer with UDP header and data for soarResponse
                        val udpPacketData = ByteBuffer.wrap(data, udpStartPosition, length - udpStartPosition)
                        udpPacketData.order(ByteOrder.BIG_ENDIAN)
                        
                        Log.d("PacketFilter", "[$direction] DNS: Creating SOAR response for blocked domain, UDP data size: ${udpPacketData.remaining()}")
                        val soarResponsePayload = soarResponse(udpPacketData, ipHeader)
                        Log.d("PacketFilter", "[$direction] DNS: SOAR response payload created, size: ${soarResponsePayload.size}")
                        
                        // Create complete response packet using Kotlin packet creation system
                        val completeResponsePacket = handleDnsResponse(udpHeader, ipHeader, soarResponsePayload)
                        Log.d("PacketFilter", "[$direction] DNS: Complete response packet created, size: ${completeResponsePacket.size}")
                        
                        // Send complete packet through VPN interface
                        if (contextPtr != 0L) {
                            jni_send_complete_packet(contextPtr, completeResponsePacket)
                        }
                        
                        Log.d("PacketFilter", "[$direction] DNS: Sent SOAR response for blocked domain")
                        false // Block original packet
                    } catch (e: Exception) {
                        Log.e("PacketFilter", "Error creating/sending SOAR response: ${e.message}", e)
                        false // Block packet on error
                    }
                } else if (isDnsPacket && rawDnsQuery != null && dohUrl != null && isDnsQuery(rawDnsQuery)) {
                    // Resolve the query over HTTPS and inject the response into the TUN.
                    // The original UDP packet is blocked so it never reaches plain DNS.
                    scheduleDohResponse(udpHeader, ipHeader, rawDnsQuery)
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

    private fun scheduleDohResponse(udpHeader: UDPModel, ipHeader: IPv4, query: ByteArray) {
        val endpoint = dohUrl ?: return
        if (contextPtr == 0L) return

        dohScope.launch {
            // Bound the number of blocking lookups so a slow or unreachable
            // DoH server cannot exhaust the shared IO pool.
            val response = dohPermits.withPermit { resolveDns(endpoint, query) }
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

    private fun resolveDns(endpoint: String, query: ByteArray): ByteArray {
        val key = dohCacheKey(query)
        synchronized(dohCacheLock) {
            dohCache[key]?.let { entry ->
                if (entry.expiresAt > System.currentTimeMillis()) {
                    return withTransactionId(entry.response, query)
                }
                dohCache.remove(key)
            }
        }

        val response = try {
            fetchDoh(endpoint, query)
        } catch (e: Exception) {
            Logger.warn("DoH: ${e.message}; falling back to plain DNS")
            try {
                fetchPlainDns(query)
            } catch (fallback: Exception) {
                Logger.warn("DoH/plain DNS fallback failed: ${fallback.message}")
                servFail(query)
            }
        }

        val normalized = if (response.size >= 12) response else servFail(query)
        val cached = normalized.copyOf().also {
            it[0] = 0
            it[1] = 0
        }
        val truncated = (normalized[2].toInt() and 0x02) != 0
        val servFailResponse = (normalized[3].toInt() and 0x0F) == 2
        if (!truncated && !servFailResponse) {
            synchronized(dohCacheLock) {
                dohCache[key] = DohCacheEntry(
                    expiresAt = System.currentTimeMillis() + DOH_CACHE_TTL_MS,
                    response = cached
                )
            }
        }
        return withTransactionId(cached, query)
    }

    private fun fetchDoh(endpoint: String, query: ByteArray): ByteArray {
        val connection = URL(endpoint).openConnection() as HttpsURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = DOH_TIMEOUT_MS
            connection.readTimeout = DOH_TIMEOUT_MS
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(query.size)
            connection.setRequestProperty("Content-Type", "application/dns-message")
            connection.setRequestProperty("Accept", "application/dns-message")
            connection.setRequestProperty("User-Agent", "BBDNS/1.0")
            connection.outputStream.use { it.write(query) }

            val code = connection.responseCode
            if (code != HttpsURLConnection.HTTP_OK) {
                throw IOException("HTTP $code from $endpoint")
            }
            val response = connection.inputStream.use { it.readBytes() }
            if (response.size < 12) throw IOException("Short DoH response")
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun fetchPlainDns(query: ByteArray): ByteArray {
        if (dnsServerV4.isBlank()) throw IOException("No fallback DNS server")
        DatagramSocket().use { socket ->
            socket.soTimeout = PLAIN_DNS_TIMEOUT_MS
            val address = InetAddress.getByName(dnsServerV4)
            socket.send(DatagramPacket(query, query.size, address, 53))
            val buffer = ByteArray(4096)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            return response.data.copyOf(response.length)
        }
    }

    private fun servFail(query: ByteArray): ByteArray {
        val response = query.copyOf()
        if (response.size >= 12) {
            response[2] = (response[2].toInt() or 0x80).toByte() // QR = response
            response[3] = ((response[3].toInt() and 0xF0) or 0x02).toByte() // RA + SERVFAIL
            for (index in 6..11) response[index] = 0 // no answer/authority/additional records
        }
        return response
    }

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
    private external fun jni_set_dns_servers(context: Long, dnsV4: String, dnsV6: String)
    private external fun jni_send_complete_packet(context: Long, packetData: ByteArray)

    companion object {
        private const val DOH_TIMEOUT_MS = 3000
        private const val DOH_MAX_CONCURRENT = 8
        private const val PLAIN_DNS_TIMEOUT_MS = 2000
        private const val DOH_CACHE_TTL_MS = 60_000L
        private const val DOH_CACHE_MAX_ENTRIES = 256

        init {
            System.loadLibrary("bbdns")
        }
    }
}