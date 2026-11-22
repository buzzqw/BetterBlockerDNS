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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs

import com.buzzqw.bbdns.domain.model.Log
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogCsvTest {

    private fun log(
        uid: Int = 10123,
        domain: String? = "ads.example.com",
        ip: String = "93.184.216.34",
        port: String = "53",
        protocol: String = "UDP",
        status: FirewallResult = FirewallResult.DNS_BLOCKED,
        time: Long = 1_700_000_000_000L,
    ) = Log(
        time = time,
        protocol = protocol,
        packageID = uid,
        sourceIP = "10.0.10.1",
        destinationAddress = domain,
        sourcePort = "4444",
        destinationIP = ip,
        destinationPort = port,
        packetStatus = status,
    )

    private val fixedTime: (Long) -> String = { "2026-11-22T09:00:00+01:00" }

    @Test
    fun `header and one row per log`() {
        val csv = LogCsv.export(
            logs = listOf(
                log(status = FirewallResult.DNS_BLOCKED),
                log(domain = "www.example.org", status = FirewallResult.ACCEPT),
            ),
            appLabel = { "Chrome" },
            timeFormatter = fixedTime,
        ).trim().lines()

        assertEquals(LogCsv.HEADER, csv[0])
        assertEquals(3, csv.size)
        assertTrue(csv[1].contains("ads.example.com"))
        assertTrue(csv[1].contains("DNS_BLOCKED"))
        assertTrue(csv[2].contains("ACCEPT"))
    }

    @Test
    fun `the app name is resolved once per uid`() {
        var calls = 0
        LogCsv.export(
            logs = listOf(log(), log(), log(domain = "b.example.com")),
            appLabel = { calls++; "Chrome" },
            timeFormatter = fixedTime,
        )
        assertEquals(1, calls)
    }

    @Test
    fun `a log without a domain exports the address`() {
        val row = LogCsv.export(
            logs = listOf(log(domain = null, ip = "1.2.3.4")),
            appLabel = { "Game" },
            timeFormatter = fixedTime,
        ).lines()[1]
        assertTrue(row.contains("1.2.3.4"))
    }

    @Test
    fun `commas and quotes are escaped`() {
        val csv = LogCsv.export(
            logs = listOf(log(domain = "weird,\"name\".example")),
            appLabel = { "App, with comma" },
            timeFormatter = fixedTime,
        )
        val row = csv.lines()[1]
        assertTrue(row.startsWith("2026-11-22T09:00:00+01:00,\"App, with comma\","))
        assertTrue(row.contains("\"weird,\"\"name\"\".example\""))
    }

    @Test
    fun `an unknown app falls back to the uid`() {
        val csv = LogCsv.export(
            logs = listOf(log(uid = 10123)),
            appLabel = { "uid:$it" },
            timeFormatter = fixedTime,
        )
        assertTrue(csv.lines()[1].contains("uid:10123"))
    }

    @Test
    fun `an empty log is only the header`() {
        assertEquals(LogCsv.HEADER + "\n", LogCsv.export(emptyList(), { "" }, fixedTime))
    }
}
