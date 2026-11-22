package com.buzzqw.bbdns.service.vpn.network.transport.udp

import com.buzzqw.bbdns.service.vpn.network.transport.ipv4.IPv4

/**
 * Wraps an answer built by [com.buzzqw.bbdns.service.vpn.network.transport.dns.DnsAnswers]
 * in a UDP/IP packet, so it can be written back into the tunnel as if it came
 * from the resolver.
 */
fun handleDnsResponse(requestPacket: UDPModel, ipHeader: IPv4, responsePayload: ByteArray): ByteArray {
    return createResponsePacket(ipHeader, requestPacket, responsePayload)
}
