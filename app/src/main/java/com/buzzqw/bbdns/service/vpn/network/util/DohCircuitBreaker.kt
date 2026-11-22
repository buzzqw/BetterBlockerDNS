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

/**
 * Stops a dead DNS-over-HTTPS endpoint from costing every query a timeout.
 *
 * When an endpoint stops answering (captive portal, blocked network, wrong
 * URL), each query used to pay the full DoH budget before falling back to
 * plain DNS. After [failureThreshold] consecutive failures the breaker opens
 * and queries go straight to the fallback for [openMillis]. Then it lets a
 * single probe through: if the probe succeeds, normal use resumes; if it
 * fails, the breaker stays closed for another window.
 *
 * Plain Kotlin with an injectable clock, so the timing is tested on the JVM
 * instead of being discovered on a user's phone.
 */
class DohCircuitBreaker(
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val openMillis: Long = DEFAULT_OPEN_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    enum class State { CLOSED, OPEN, PROBING }

    private var failures = 0
    private var openedAt = 0L
    private var probing = false

    /** True while normal DoH lookups may run. */
    @Synchronized
    fun allowRequest(): Boolean {
        if (failures < failureThreshold) return true
        val now = clock()
        if (now - openedAt < openMillis) return false
        // Window elapsed: exactly one request goes out as a probe.
        if (probing) return false
        probing = true
        return true
    }

    @Synchronized
    fun recordSuccess() {
        failures = 0
        probing = false
    }

    @Synchronized
    fun recordFailure() {
        probing = false
        failures++
        if (failures >= failureThreshold) openedAt = clock()
    }

    @Synchronized
    fun state(): State = when {
        failures < failureThreshold -> State.CLOSED
        probing -> State.PROBING
        else -> State.OPEN
    }

    /** For diagnostics: consecutive failures since the last success. */
    @Synchronized
    fun failureCount(): Int = failures

    companion object {
        const val DEFAULT_FAILURE_THRESHOLD = 3
        const val DEFAULT_OPEN_MILLIS = 60_000L
    }
}
