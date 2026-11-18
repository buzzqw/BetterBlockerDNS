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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DnsProtectionStateTest {

    @Before
    fun setUp() {
        DnsProtectionState.setDohBypassConfigured(false)
        DnsProtectionState.setDnsBlockingEnabled(true)
    }

    @After
    fun tearDown() {
        DnsProtectionState.setDohBypassConfigured(false)
        DnsProtectionState.setDnsBlockingEnabled(true)
    }

    @Test
    fun `doh bypass is active only when configured and blocking is on`() {
        assertFalse(DnsProtectionState.isDohBypassActive)

        DnsProtectionState.setDohBypassConfigured(true)
        assertTrue(DnsProtectionState.isDohBypassActive)

        DnsProtectionState.setDnsBlockingEnabled(false)
        assertFalse(DnsProtectionState.isDohBypassActive)
    }

    @Test
    fun `a pause does not forget the configured switch`() {
        DnsProtectionState.setDohBypassConfigured(true)
        DnsProtectionState.setDnsBlockingEnabled(false)
        assertFalse(DnsProtectionState.isDohBypassActive)

        DnsProtectionState.setDnsBlockingEnabled(true)
        assertTrue(DnsProtectionState.isDohBypassActive)
    }

    @Test
    fun `resuming without the switch never enables the bypass`() {
        DnsProtectionState.setDnsBlockingEnabled(false)
        DnsProtectionState.setDnsBlockingEnabled(true)
        assertFalse(DnsProtectionState.isDohBypassActive)
    }
}
