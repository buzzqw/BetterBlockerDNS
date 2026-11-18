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

/**
 * Live state of the DNS protections that are independent of the blocklists.
 *
 * The packet thread reads it on every SYN/DNS query, so the values are
 * volatile and the getter stays allocation-free. The *Block DNS bypass*
 * switch is suspended together with DNS blocking: while protection is paused
 * the encrypted endpoints must work again, and resuming restores the switch
 * only if the user had turned it on.
 */
object DnsProtectionState {

    @Volatile
    private var dohBypassConfigured: Boolean = false

    @Volatile
    private var dnsBlockingEnabled: Boolean = true

    val isDohBypassActive: Boolean
        get() = dohBypassConfigured && dnsBlockingEnabled

    fun setDohBypassConfigured(enabled: Boolean) {
        dohBypassConfigured = enabled
    }

    fun setDnsBlockingEnabled(enabled: Boolean) {
        dnsBlockingEnabled = enabled
    }
}
