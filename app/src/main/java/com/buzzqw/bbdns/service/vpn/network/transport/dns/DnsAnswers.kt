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

import org.xbill.DNS.ARecord
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.SOARecord
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.InetAddress

/**
 * DNS replies the tunnel builds itself instead of forwarding them upstream.
 *
 * Everything here works on wire-format messages and has no Android
 * dependency, so the awkward part of a DNS filter (the answers it invents)
 * can be tested on a normal JVM.
 */
object DnsAnswers {

    /** Negative answer, as used for a blocked query. */
    private const val NEGATIVE_TTL_SECONDS = 5L

    /** Answer TTL for a redirect to the local notice page: short, so the
     *  setting and the "continue" choice take effect without waiting. */
    const val NOTICE_TTL_SECONDS = 30L

    /** A CNAME chain longer than this is treated as hostile and ignored. */
    private const val MAX_CNAME_CHAIN = 16

    private val NEGATIVE_NAME = Name("dnsnet.dnsnet.invalid.")

    /**
     * Every CNAME target in the answer section, in the order they appear.
     *
     * A resolver can legitimately answer a blocked name with an alias: the
     * queried name is not on any list, but the name it points to is. Reading
     * the chain is what makes that visible before the app connects.
     * Returns an empty list for responses that do not parse, never throws.
     */
    fun cnameTargets(response: ByteArray): List<String> {
        if (response.size < 12) return emptyList()
        val targets = LinkedHashSet<String>()
        try {
            val message = Message(response)
            for (record in message.getSection(Section.ANSWER)) {
                if (record.type != Type.CNAME) continue
                val target = (record as? CNAMERecord)?.target ?: continue
                val name = normalize(target.toString())
                if (name.isEmpty()) continue
                targets += name
                if (targets.size >= MAX_CNAME_CHAIN) break
            }
        } catch (e: Exception) {
            return emptyList()
        }
        return targets.toList()
    }

    /**
     * The negative answer for a blocked query: no address, a short SOA in the
     * authority section and a TTL that keeps the client from retrying for a
     * few seconds instead of hammering the filter.
     */
    fun blockedReply(query: ByteArray): ByteArray {
        val request = parse(query) ?: return servFail(query)
        val question = request.question ?: return servFail(query)
        return try {
            val response = newResponse(request)
            response.header.rcode = Rcode.NOERROR
            response.addRecord(question, Section.QUESTION)
            response.addRecord(negativeSoa(), Section.AUTHORITY)
            response.toWire()
        } catch (e: Exception) {
            servFail(query)
        }
    }

    /**
     * The answer that sends the browser to the local notice page.
     *
     * Only A gets an address. AAAA is answered empty on purpose: a client that
     * asks for IPv6 falls back to the IPv4 answer instead of waiting for a
     * timeout, and no IPv6 route to the notice page is needed. Every other
     * record type keeps the plain blocked answer, so mail and certificates
     * are not redirected to a web server.
     */
    fun noticeReply(query: ByteArray, address: String, ttl: Long = NOTICE_TTL_SECONDS): ByteArray {
        val request = parse(query) ?: return servFail(query)
        val question = request.question ?: return servFail(query)
        if (question.type != Type.A) return blockedReply(query)

        return try {
            val target = InetAddress.getByName(address)
            val response = newResponse(request)
            response.header.rcode = Rcode.NOERROR
            response.addRecord(question, Section.QUESTION)
            response.addRecord(ARecord(question.name, DClass.IN, ttl, target), Section.ANSWER)
            response.toWire()
        } catch (e: Exception) {
            servFail(query)
        }
    }

    /** SERVFAIL for a query with a valid header, so the client retries later. */
    fun servFail(query: ByteArray): ByteArray {
        if (query.size < 12) return query.copyOf()
        val response = query.copyOf()
        response[2] = (response[2].toInt() or 0x80).toByte() // QR = response
        response[3] = ((response[3].toInt() and 0xF0) or Rcode.SERVFAIL).toByte()
        for (index in 6..11) response[index] = 0 // no answer/authority/additional records
        return response
    }

    /** True when [wire] is a DNS query (QR = 0), false for a response. */
    fun isQuery(wire: ByteArray): Boolean =
        wire.size >= 12 && (wire[2].toInt() and 0x80) == 0

    private fun parse(query: ByteArray): Message? = try {
        if (query.size < 12) null else Message(query)
    } catch (e: Exception) {
        null
    }

    private fun newResponse(request: Message): Message {
        val response = Message(request.header.getID())
        response.header.setFlag(Flags.QR.toInt())
        response.header.setFlag(Flags.RA.toInt())
        if (request.header.getFlag(Flags.RD.toInt())) {
            response.header.setFlag(Flags.RD.toInt())
        }
        return response
    }

    private fun negativeSoa(): SOARecord = SOARecord(
        NEGATIVE_NAME,
        DClass.IN,
        NEGATIVE_TTL_SECONDS,
        NEGATIVE_NAME,
        NEGATIVE_NAME,
        0,
        0,
        0,
        0,
        NEGATIVE_TTL_SECONDS,
    )

    private fun normalize(name: String): String =
        name.trim().trimEnd('.').lowercase()
}
