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

package com.buzzqw.bbdns.service.vpn.network.util

import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * DNS-level SafeSearch enforcement.
 *
 * Google, Bing and YouTube expose dedicated hostnames whose addresses serve
 * the result pages with SafeSearch (or YouTube restricted mode) forced on.
 * Answering the original query with one of those addresses is how every DNS
 * filter enforces it, and it needs no certificate and no TLS interception.
 *
 * Only A and AAAA are redirected: browsers and apps fall back to them, while
 * other record types (HTTPS/SVCB, MX, TXT) are left to the normal resolver so
 * that mail, certificates and service discovery keep working.
 */
object SafeSearch {

    const val GOOGLE_TARGET = "forcesafesearch.google.com"
    const val BING_TARGET = "strict.bing.com"
    const val YOUTUBE_TARGET = "restrict.youtube.com"

    /** Short TTL so turning the setting off takes effect quickly. */
    const val TTL_SECONDS = 300L

    private val YOUTUBE_DOMAINS = listOf(
        "youtube.com",
        "youtube-nocookie.com",
    )

    /** The redirect targets themselves must keep resolving normally. */
    private val TARGET_HOSTS = setOf(GOOGLE_TARGET, BING_TARGET, YOUTUBE_TARGET)

    /**
     * Returns the hostname whose address should answer [host], or null when
     * the query is not one of the domains SafeSearch covers.
     *
     * [queryType] must be the numeric DNS type from the query (1 = A,
     * 28 = AAAA); other types are never redirected.
     */
    fun targetFor(host: String, queryType: Int): String? {
        if (queryType != Type.A && queryType != Type.AAAA) return null
        val normalized = normalize(host)
        if (normalized.isEmpty() || normalized in TARGET_HOSTS) return null
        if (isGoogleSearch(normalized)) return GOOGLE_TARGET
        if (normalized == "bing.com" || normalized.endsWith(".bing.com")) return BING_TARGET
        if (YOUTUBE_DOMAINS.any { normalized == it || normalized.endsWith(".$it") }) {
            return YOUTUBE_TARGET
        }
        return null
    }

    /**
     * Builds the DNS response for [query] pointing at [address].
     *
     * The question section is copied verbatim from the request so that clients
     * which randomize the case of the query name still accept the answer.
     * Returns null when the query is malformed or the address family does not
     * match the question type.
     */
    fun buildResponse(query: ByteArray, address: InetAddress): ByteArray? {
        return try {
            val request = Message(query)
            val question = request.question ?: return null
            val type = question.type
            val answer: Record = when {
                type == Type.A && address is Inet4Address ->
                    ARecord(question.name, DClass.IN, TTL_SECONDS, address)
                type == Type.AAAA && address is Inet6Address ->
                    AAAARecord(question.name, DClass.IN, TTL_SECONDS, address)
                else -> return null
            }

            val response = Message(request.header.getID())
            response.header.setFlag(Flags.QR.toInt())
            response.header.setFlag(Flags.RA.toInt())
            if (request.header.getFlag(Flags.RD.toInt())) {
                response.header.setFlag(Flags.RD.toInt())
            }
            response.header.rcode = Rcode.NOERROR
            response.addRecord(question, Section.QUESTION)
            response.addRecord(answer, Section.ANSWER)
            response.toWire()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * True for google.com, google.it, www.google.co.uk, images.google.com…
     * but false for googleapis.com and googleusercontent.com, which are
     * infrastructure and must keep resolving normally.
     */
    private fun isGoogleSearch(host: String): Boolean {
        val labels = host.split('.')
        val index = labels.indexOf("google")
        // "google" must be followed by a TLD (google.com) or by a
        // second-level + TLD pair (google.co.uk); anything longer is a
        // different domain that happens to contain the label.
        val labelsAfter = labels.size - index - 1
        return index >= 0 && labelsAfter in 1..2
    }

    private fun normalize(host: String): String =
        host.trim().trimEnd('.').lowercase()
}
