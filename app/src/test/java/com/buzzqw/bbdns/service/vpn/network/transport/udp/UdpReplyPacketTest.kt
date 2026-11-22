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

package com.buzzqw.bbdns.service.vpn.network.transport.udp

import com.buzzqw.bbdns.service.vpn.network.transport.ipv4.toIPv4Header
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xbill.DNS.Message
import org.xbill.DNS.Record
import org.xbill.DNS.Name
import org.xbill.DNS.DClass
import org.xbill.DNS.Type
import org.xbill.DNS.Section
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The packet path that builds a DNS reply does its own IPv4/UDP framing. A
 * mistake there is an invisible packet that Android drops, so the framing is
 * checked here rather than on a phone.
 */
class UdpReplyPacketTest {

    private fun dnsQuery(name: String): ByteArray {
        val message = Message(0xABCD)
        message.addRecord(Record.newRecord(Name.fromString("$name."), Type.A, DClass.IN), Section.QUESTION)
        return message.toWire()
    }

    /** One IPv4 + UDP packet carrying [payload], from 10.0.10.1:4444 to 198.18.0.2:53. */
    private fun requestPacket(payload: ByteArray): ByteArray {
        val packet = ByteArray(20 + 8 + payload.size)
        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)
        buffer.put(0x45)                       // IPv4, 5 words of header
        buffer.put(0)                          // DSCP/ECN
        buffer.putShort((20 + 8 + payload.size).toShort())
        buffer.putShort(0x1234)                // identification
        buffer.putShort(0)                     // flags/fragment
        buffer.put(64)                         // TTL
        buffer.put(17)                         // UDP
        buffer.putShort(0)                     // checksum, not verified here
        buffer.put(byteArrayOf(10, 0, 10, 1))  // source
        buffer.put(byteArrayOf(198.toByte(), 18, 0, 2)) // destination
        buffer.putShort(4444.toShort())        // source port
        buffer.putShort(53)                    // destination port
        buffer.putShort((8 + payload.size).toShort())
        buffer.putShort(0)                     // checksum
        buffer.put(payload)
        return packet
    }

    @Test
    fun `a reply swaps the endpoints and keeps the payload`() {
        val query = dnsQuery("blocked.example.com")
        val request = requestPacket(query)
        val buffer = ByteBuffer.wrap(request).order(ByteOrder.BIG_ENDIAN)

        val ipHeader = buffer.toIPv4Header()
        val udpHeader = buffer.toUDPHeader()
        val extracted = udpHeader.extractUDPData(buffer)
        assertArrayEquals(query, extracted)

        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val reply = handleDnsResponse(udpHeader, ipHeader, payload)

        // Header lengths add up to the packet that is written into the tunnel.
        assertEquals(20 + 8 + payload.size, reply.size)
        val replyBuffer = ByteBuffer.wrap(reply).order(ByteOrder.BIG_ENDIAN)
        val replyIp = replyBuffer.toIPv4Header()
        val replyUdp = replyBuffer.toUDPHeader()

        assertEquals(0x45, reply[0].toInt() and 0xFF)
        assertEquals(17.toByte(), replyIp.protocol)
        assertEquals(53, replyUdp.sourcePort)
        assertEquals(4444, replyUdp.destinationPort)
        assertEquals(8 + payload.size, replyUdp.length)
        assertArrayEquals(payload, reply.copyOfRange(28, reply.size))

        // Source and destination are exchanged: the answer goes back to the
        // app that asked, from the resolver address it used.
        assertEquals(ipHeader.destinationIP, replyIp.sourceIP)
        assertEquals(ipHeader.sourceIP, replyIp.destinationIP)
    }

    @Test
    fun `a dns answer built on the phone still parses after framing`() {
        val query = dnsQuery("ads.example.com")
        val request = requestPacket(query)
        val buffer = ByteBuffer.wrap(request).order(ByteOrder.BIG_ENDIAN)
        val ipHeader = buffer.toIPv4Header()
        val udpHeader = buffer.toUDPHeader()

        val answer = com.buzzqw.bbdns.service.vpn.network.transport.dns.DnsAnswers
            .noticeReply(query, "198.18.0.3")
        val reply = handleDnsResponse(udpHeader, ipHeader, answer)

        val replyPayload = reply.copyOfRange(28, reply.size)
        val message = Message(replyPayload)
        assertEquals(0xABCD, message.header.getID())
        assertEquals(1, message.getSection(Section.ANSWER).size)
    }
}
