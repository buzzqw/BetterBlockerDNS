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

package com.buzzqw.bbdns.service.firewall.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FirewallCountersTest {

    @Before
    @After
    fun clearCounters() {
        FirewallCounters.reset()
    }

    @Test
    fun `blocks are counted per source and sorted by count`() {
        FirewallCounters.record(false, "a.example.com", 1, "List A")
        FirewallCounters.record(false, "b.example.com", 1, "List B")
        FirewallCounters.record(false, "c.example.com", 1, "List A")
        FirewallCounters.record(true, "d.example.com", 1, "List A")

        assertEquals(
            listOf("List A" to 2L, "List B" to 1L),
            FirewallCounters.topBlockSources(),
        )
        assertEquals(3L, FirewallCounters.blockSourcesTotal())
        assertEquals(3L, FirewallCounters.snapshot().second)
    }

    @Test
    fun `blocks without a known source are not attributed to a list`() {
        FirewallCounters.record(false, "a.example.com", 1)
        FirewallCounters.record(false, "b.example.com", 1, "  ")

        assertEquals(emptyList<Pair<String, Long>>(), FirewallCounters.topBlockSources())
        assertEquals(0L, FirewallCounters.blockSourcesTotal())
        assertEquals(2L, FirewallCounters.snapshot().second)
    }

    @Test
    fun `reset clears per-source counters`() {
        FirewallCounters.record(false, "a.example.com", 1, FirewallCounters.SOURCE_CUSTOM)

        FirewallCounters.reset()

        assertEquals(emptyList<Pair<String, Long>>(), FirewallCounters.topBlockSources())
        assertEquals(0L, FirewallCounters.snapshot().second)
    }
}
