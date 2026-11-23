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
import org.junit.Test
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.Section
import org.xbill.DNS.Type

class CnameCloakGuardTest {

    private val blocked = setOf("tracker.adnetwork.net", "ads.tracker.io")
    private val explicitlyAllowed = setOf("shop.example.com", "good.tracker.io")

    private fun response(queried: String, targets: List<String>): ByteArray {
        val message = Message(1)
        message.header.setFlag(Flags.QR.toInt())
        message.addRecord(Record.newRecord(Name.fromString("$queried."), Type.A, DClass.IN), Section.QUESTION)
        targets.forEach { target ->
            message.addRecord(
                CNAMERecord(Name.fromString("$queried."), DClass.IN, 30, Name.fromString("$target.")),
                Section.ANSWER,
            )
        }
        return message.toWire()
    }

    private fun find(queried: String, targets: List<String>) = CnameCloakGuard.find(
        response = response(queried, targets),
        queried = queried,
        isBlocked = { it in blocked },
        isExplicitlyAllowed = { it in explicitlyAllowed },
    )

    @Test
    fun `an allowed name pointing at a blocked alias is a cloak`() {
        val cloak = find("metrics.example.com", listOf("tracker.adnetwork.net"))
        assertEquals("metrics.example.com", cloak?.domain)
        assertEquals("tracker.adnetwork.net", cloak?.target)
    }

    @Test
    fun `the alias is found anywhere in the chain`() {
        val cloak = find(
            "pixel.example.com",
            listOf("edge.cdn.example.net", "tracker.adnetwork.net"),
        )
        assertEquals("tracker.adnetwork.net", cloak?.target)
    }

    @Test
    fun `a clean chain passes`() {
        assertNull(find("www.example.com", listOf("edge.cdn.example.net")))
        assertNull(find("www.example.com", emptyList()))
    }

    @Test
    fun `an explicit allow on the queried name wins`() {
        assertNull(find("shop.example.com", listOf("tracker.adnetwork.net")))
    }

    @Test
    fun `an explicit allow on the alias wins`() {
        assertNull(find("metrics.example.com", listOf("good.tracker.io")))
    }

    @Test
    fun `a name already blocked by the normal path is not handled twice`() {
        assertNull(find("tracker.adnetwork.net", listOf("ads.tracker.io")))
    }

    @Test
    fun `case and trailing dots do not hide an alias`() {
        val message = response("Metrics.Example.COM", listOf("Tracker.AdNetwork.NET"))
        val cloak = CnameCloakGuard.find(
            response = message,
            queried = "metrics.example.com.",
            isBlocked = { it in blocked },
        )
        assertEquals("tracker.adnetwork.net", cloak?.target)
    }

    @Test
    fun `a response that does not parse is ignored`() {
        assertNull(CnameCloakGuard.find(ByteArray(4), "metrics.example.com", { it in blocked }))
    }

    @Test
    fun `an app excluded from DNS filtering keeps its cloaked answer`() {
        // The excluded app's question was never checked against the lists, so
        // the answer must not be checked either.
        val cloak = CnameCloakGuard.find(
            response = response("metrics.example.com", listOf("tracker.adnetwork.net")),
            queried = "metrics.example.com",
            isBlocked = { it in blocked },
            uid = 10145,
            isExcluded = { it == 10145 },
        )
        assertNull(cloak)
    }

    @Test
    fun `an unknown UID is still checked`() {
        val cloak = CnameCloakGuard.find(
            response = response("metrics.example.com", listOf("tracker.adnetwork.net")),
            queried = "metrics.example.com",
            isBlocked = { it in blocked },
            isExcluded = { true },
        )
        assertEquals("tracker.adnetwork.net", cloak?.target)
    }
}
