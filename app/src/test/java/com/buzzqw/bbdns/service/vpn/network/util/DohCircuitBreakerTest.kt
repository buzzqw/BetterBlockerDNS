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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DohCircuitBreakerTest {

    private var now = 1_000_000L
    private fun breaker() = DohCircuitBreaker(
        failureThreshold = 3,
        openMillis = 60_000,
        clock = { now },
    )

    @Test
    fun `stays closed while lookups succeed`() {
        val breaker = breaker()
        repeat(20) {
            assertTrue(breaker.allowRequest())
            breaker.recordSuccess()
        }
        assertEquals(DohCircuitBreaker.State.CLOSED, breaker.state())
    }

    @Test
    fun `opens after the configured number of failures`() {
        val breaker = breaker()
        repeat(2) {
            assertTrue(breaker.allowRequest())
            breaker.recordFailure()
        }
        assertTrue(breaker.allowRequest())
        breaker.recordFailure()
        assertEquals(DohCircuitBreaker.State.OPEN, breaker.state())
        assertFalse(breaker.allowRequest())
    }

    @Test
    fun `a success before the threshold resets the counter`() {
        val breaker = breaker()
        repeat(2) { breaker.recordFailure() }
        breaker.recordSuccess()
        repeat(2) { breaker.recordFailure() }
        assertTrue(breaker.allowRequest())
        assertEquals(DohCircuitBreaker.State.CLOSED, breaker.state())
    }

    @Test
    fun `after the window one probe goes out and nothing else`() {
        val breaker = breaker()
        repeat(3) { breaker.recordFailure() }
        assertFalse(breaker.allowRequest())

        now += 60_001
        assertTrue(breaker.allowRequest())
        assertFalse(breaker.allowRequest())
        assertEquals(DohCircuitBreaker.State.PROBING, breaker.state())

        breaker.recordSuccess()
        assertTrue(breaker.allowRequest())
        assertEquals(DohCircuitBreaker.State.CLOSED, breaker.state())
    }

    @Test
    fun `a failed probe keeps the breaker open for another window`() {
        val breaker = breaker()
        repeat(3) { breaker.recordFailure() }
        now += 60_001
        assertTrue(breaker.allowRequest())
        breaker.recordFailure()

        assertFalse(breaker.allowRequest())
        now += 60_001
        assertTrue(breaker.allowRequest())
    }
}
