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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.net.InetAddress

class SafeSearchTest {

    @Test
    fun `google search domains are redirected`() {
        listOf(
            "google.com",
            "www.google.com",
            "google.it",
            "www.google.co.uk",
            "images.google.com",
            "GOOGLE.COM."
        ).forEach { host ->
            assertEquals(host, SafeSearch.GOOGLE_TARGET, SafeSearch.targetFor(host, Type.A))
        }
    }

    @Test
    fun `google infrastructure is not redirected`() {
        listOf(
            "googleapis.com",
            "www.googleapis.com",
            "googleusercontent.com",
            "notgoogle.com",
            "google.com.evil.example"
        ).forEach { host ->
            assertNull(host, SafeSearch.targetFor(host, Type.A))
        }
    }

    @Test
    fun `bing is redirected but other sites are not`() {
        assertEquals(SafeSearch.BING_TARGET, SafeSearch.targetFor("bing.com", Type.A))
        assertEquals(SafeSearch.BING_TARGET, SafeSearch.targetFor("www.bing.com", Type.A))
        assertNull(SafeSearch.targetFor("bing.example", Type.A))
        assertNull(SafeSearch.targetFor("notbing.com", Type.A))
    }

    @Test
    fun `youtube and nocookie are redirected`() {
        assertEquals(SafeSearch.YOUTUBE_TARGET, SafeSearch.targetFor("youtube.com", Type.A))
        assertEquals(SafeSearch.YOUTUBE_TARGET, SafeSearch.targetFor("m.youtube.com", Type.A))
        assertEquals(
            SafeSearch.YOUTUBE_TARGET,
            SafeSearch.targetFor("www.youtube-nocookie.com", Type.A)
        )
        assertNull(SafeSearch.targetFor("youtu.be", Type.A))
        assertNull(SafeSearch.targetFor("googlevideo.com", Type.A))
    }

    @Test
    fun `redirect targets resolve normally`() {
        listOf(
            SafeSearch.GOOGLE_TARGET,
            SafeSearch.BING_TARGET,
            SafeSearch.YOUTUBE_TARGET
        ).forEach { host ->
            assertNull(host, SafeSearch.targetFor(host, Type.A))
        }
    }

    @Test
    fun `only A and AAAA are redirected`() {
        listOf(Type.MX, Type.TXT, Type.HTTPS, Type.CNAME).forEach { type ->
            assertNull("type $type", SafeSearch.targetFor("www.google.com", type))
        }
        assertEquals(SafeSearch.GOOGLE_TARGET, SafeSearch.targetFor("www.google.com", Type.AAAA))
    }

    @Test
    fun `builds an A answer with the original question`() {
        val query = query("www.google.com", Type.A)
        val address = InetAddress.getByName("216.239.38.120")
        val response = SafeSearch.buildResponse(query, address)
        requireNotNull(response)

        val message = Message(response)
        assertEquals(0x4242, message.header.getID())
        assertTrue(message.header.getFlag(Flags.QR.toInt()))
        assertTrue(message.header.getFlag(Flags.RD.toInt()))
        assertEquals(Rcode.NOERROR, message.header.rcode)
        assertEquals(1, message.getSection(Section.QUESTION).size)
        assertEquals(1, message.getSection(Section.ANSWER).size)
        val answer = message.getSection(Section.ANSWER)[0] as ARecord
        assertEquals(address, answer.address)
        assertEquals("www.google.com.", answer.name.toString())
        assertEquals(SafeSearch.TTL_SECONDS, answer.getTTL())
    }

    @Test
    fun `builds an AAAA answer`() {
        val query = query("youtube.com", Type.AAAA)
        val address = InetAddress.getByName("2001:4860:4802:32::78")
        val response = SafeSearch.buildResponse(query, address)
        requireNotNull(response)

        val message = Message(response)
        assertEquals(1, message.getSection(Section.ANSWER).size)
        assertEquals(
            address,
            (message.getSection(Section.ANSWER)[0] as org.xbill.DNS.AAAARecord).address
        )
    }

    @Test
    fun `rejects address family mismatches and malformed queries`() {
        assertNull(
            SafeSearch.buildResponse(
                query("www.google.com", Type.A),
                InetAddress.getByName("2001:4860:4802:32::78")
            )
        )
        assertNull(
            SafeSearch.buildResponse(
                query("www.google.com", Type.AAAA),
                InetAddress.getByName("216.239.38.120")
            )
        )
        assertNull(SafeSearch.buildResponse(ByteArray(0), InetAddress.getByName("1.2.3.4")))
    }

    private fun query(name: String, type: Int): ByteArray {
        val message = Message()
        message.header.setID(0x4242)
        message.header.setFlag(Flags.RD.toInt())
        message.addRecord(
            Record.newRecord(Name.fromString("$name."), type, DClass.IN),
            Section.QUESTION
        )
        return message.toWire()
    }
}
