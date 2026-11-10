/*
 * Copyright (C) 2026 Vexzure
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

/**
 * Drops the tunnel's open sessions when a firewall rule changes.
 *
 * The native tunnel asks the Kotlin rules only about the first packet of a
 * TCP or UDP flow (plus every DNS query); the rest of the flow is forwarded
 * without a JNI call, which saves most of the CPU spent on traffic. A rule
 * that starts blocking an app, an IP or the screen-off traffic must therefore
 * end the flows already open, so that their next packet is checked again.
 */
object SessionInvalidator {
    @Volatile
    private var listener: (() -> Unit)? = null

    /** Set by the VPN service while the tunnel runs; null when it stops. */
    fun setListener(callback: (() -> Unit)?) {
        listener = callback
    }

    fun invalidate() {
        listener?.invoke()
    }
}
