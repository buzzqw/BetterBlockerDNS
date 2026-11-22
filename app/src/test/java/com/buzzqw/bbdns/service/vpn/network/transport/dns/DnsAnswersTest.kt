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

package com.buzzqw.bbdns.service.vpn.network.transport.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.InetAddress

class DnsAnswersTest {

    private fun query(name: String, type: Int = Type.A, id: Int = 0x1234): ByteArray {
        val message = Message(id)
        message.header.setFlag(Flags.RD.toInt())
        message.addRecord(Record.newRecord(Name.fromString("$name."), type, DClass.IN), Section.QUESTION)
        return message.toWire()
    }

    @Test
    fun `notice answer points an A query at the local address and keeps the id`() {
        val request = query("ads.example.com")
        val reply = Message(DnsAnswers.noticeReply(request, "198.18.0.3"))

        assertEquals(0x1234, reply.header.getID())
        assertTrue(reply.header.getFlag(Flags.QR.toInt()))
        assertTrue(reply.header.getFlag(Flags.RA.toInt()))
        assertTrue(reply.header.getFlag(Flags.RD.toInt()))
        assertEquals(Rcode.NOERROR, reply.header.rcode)

        val answer = reply.getSection(Section.ANSWER).single() as ARecord
        assertEquals("198.18.0.3", answer.address.hostAddress)
        assertEquals(Name.fromString("ads.example.com."), answer.name)
        assertEquals(DnsAnswers.NOTICE_TTL_SECONDS, answer.ttl)
    }

    @Test
    fun `aaaa queries are answered empty so the client falls back to ipv4`() {
        val reply = Message(DnsAnswers.noticeReply(query("ads.example.com", Type.AAAA), "198.18.0.3"))
        assertEquals(Rcode.NOERROR, reply.header.rcode)
        assertTrue(reply.getSection(Section.ANSWER).isEmpty())
    }

    @Test
    fun `other record types keep the plain blocked answer`() {
        val reply = Message(DnsAnswers.noticeReply(query("ads.example.com", Type.MX), "198.18.0.3"))
        assertTrue(reply.getSection(Section.ANSWER).isEmpty())
        assertEquals(Type.SOA, reply.getSection(Section.AUTHORITY).single().type)
    }

    @Test
    fun `blocked answer carries a short negative soa and no address`() {
        val reply = Message(DnsAnswers.blockedReply(query("tracker.example.net")))
        assertEquals(Rcode.NOERROR, reply.header.rcode)
        assertTrue(reply.getSection(Section.ANSWER).isEmpty())
        val soa = reply.getSection(Section.AUTHORITY).single()
        assertEquals(Type.SOA, soa.type)
        assertEquals(5L, soa.ttl)
    }

    @Test
    fun `cname targets are read in order without the trailing dot`() {
        val response = cnameResponse(
            "metrics.example.com" to "edge.cdn.example.net",
            "edge.cdn.example.net" to "tracker.adnetwork.net",
        )
        assertEquals(
            listOf("edge.cdn.example.net", "tracker.adnetwork.net"),
            DnsAnswers.cnameTargets(response),
        )
    }

    @Test
    fun `cname targets are deduplicated`() {
        val response = cnameResponse(
            "a.example.com" to "same.example.net",
            "b.example.com" to "same.example.net",
        )
        assertEquals(listOf("same.example.net"), DnsAnswers.cnameTargets(response))
    }

    @Test
    fun `a response without aliases has no cname targets`() {
        val message = Message(9)
        message.header.setFlag(Flags.QR.toInt())
        message.addRecord(Record.newRecord(Name.fromString("example.com."), Type.A, DClass.IN), Section.QUESTION)
        message.addRecord(
            ARecord(Name.fromString("example.com."), DClass.IN, 60, InetAddress.getByName("93.184.216.34")),
            Section.ANSWER,
        )
        assertTrue(DnsAnswers.cnameTargets(message.toWire()).isEmpty())
    }

    @Test
    fun `garbage never throws and never reports an alias`() {
        assertTrue(DnsAnswers.cnameTargets(ByteArray(0)).isEmpty())
        assertTrue(DnsAnswers.cnameTargets(byteArrayOf(1, 2, 3)).isEmpty())
        assertTrue(DnsAnswers.cnameTargets(ByteArray(64) { 0xFF.toByte() }).isEmpty())
    }

    @Test
    fun `servfail keeps the id and answers the question without records`() {
        val reply = Message(DnsAnswers.servFail(query("example.com")))
        assertEquals(0x1234, reply.header.getID())
        assertEquals(Rcode.SERVFAIL, reply.header.rcode)
        assertTrue(reply.header.getFlag(Flags.QR.toInt()))
    }

    @Test
    fun `isQuery tells questions from answers`() {
        assertTrue(DnsAnswers.isQuery(query("example.com")))
        assertFalse(DnsAnswers.isQuery(DnsAnswers.blockedReply(query("example.com"))))
        assertFalse(DnsAnswers.isQuery(byteArrayOf(1, 2, 3)))
    }

    private fun cnameResponse(vararg pairs: Pair<String, String>): ByteArray {
        val message = Message(0x4242)
        message.header.setFlag(Flags.QR.toInt())
        message.header.setFlag(Flags.RA.toInt())
        message.addRecord(
            Record.newRecord(Name.fromString("${pairs.first().first}."), Type.A, DClass.IN),
            Section.QUESTION,
        )
        for ((owner, target) in pairs) {
            message.addRecord(
                CNAMERecord(Name.fromString("$owner."), DClass.IN, 60, Name.fromString("$target.")),
                Section.ANSWER,
            )
        }
        return message.toWire()
    }
}
