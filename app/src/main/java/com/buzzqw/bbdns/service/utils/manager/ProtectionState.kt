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

package com.buzzqw.bbdns.service.utils.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process marker of the VPN/protection service state. The watchdog uses it
 * to distinguish "service was killed by the system" from "user turned it off".
 */
object ProtectionState {
    @Volatile
    var isRunning: Boolean = false

    /**
     * Set when the user explicitly turns protection off. The VPN service is
     * stopped through stopService(), so the stop action never reaches
     * onStartCommand: this flag carries the intent to onDestroy().
     */
    @Volatile
    var userStopRequested: Boolean = false

    /** True when Android revoked the VPN, usually because another VPN app started. */
    @Volatile
    var revokedByOtherVpn: Boolean = false

    /** Whether Android "Always-on VPN" is set for BBDNS (known on API 29+). */
    @Volatile
    var alwaysOn: Boolean? = null

    /** Wall-clock time when the current VPN session started, 0 if none. */
    @Volatile
    var sessionStartedAt: Long = 0L

    private val _pausedUntil = MutableStateFlow(0L)

    /**
     * Wall-clock time when a temporary pause of DNS blocking ends, 0 when
     * protection is not paused. Driven by [ProtectionPause].
     */
    val pausedUntil: StateFlow<Long> = _pausedUntil.asStateFlow()

    /** True while DNS blocking is temporarily paused. */
    val isPaused: Boolean
        get() = _pausedUntil.value > 0L

    internal fun setPausedUntil(value: Long) {
        _pausedUntil.value = value
    }
}
