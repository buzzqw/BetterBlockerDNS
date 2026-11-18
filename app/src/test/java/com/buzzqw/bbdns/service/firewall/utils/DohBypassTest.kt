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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DohBypassTest {

    @Test
    fun `matches dedicated DoH hosts and their subdomains`() {
        assertTrue(DohBypass.matches("dns.google"))
        assertTrue(DohBypass.matches("DNS.GOOGLE."))
        assertTrue(DohBypass.matches("foo.dns.google"))
        assertTrue(DohBypass.matches("mozilla.cloudflare-dns.com"))
        assertTrue(DohBypass.matches("abc123.dns.nextdns.io"))
        assertTrue(DohBypass.matches(DohBypass.CANARY_DOMAIN))
    }

    @Test
    fun `does not match unrelated hosts that merely contain a name`() {
        assertFalse(DohBypass.matches("notdns.google.evil.com"))
        assertFalse(DohBypass.matches("dns.google.example"))
        assertFalse(DohBypass.matches("exampledns.google"))
        assertFalse(DohBypass.matches("example.com"))
        assertFalse(DohBypass.matches(""))
        assertFalse(DohBypass.matches("."))
    }

    @Test
    fun `known public DoH addresses are blocked by address`() {
        listOf("1.1.1.1", "8.8.8.8", "9.9.9.9", "94.140.14.14").forEach { ip ->
            assertTrue(ip, DohBypass.isKnownDohIp(ip))
        }
        assertTrue(DohBypass.isKnownDohIp(" 1.1.1.1 "))
        assertFalse(DohBypass.isKnownDohIp("1.1.1.10"))
        assertFalse(DohBypass.isKnownDohIp("192.168.1.1"))
        assertFalse(DohBypass.isKnownDohIp(""))
    }

    @Test
    fun `drops DoT and hardcoded DoH transports`() {
        // DoT: TCP 853, DNS-over-QUIC: UDP 853.
        assertTrue(DohBypass.blocksTransport(TCP, 853, "203.0.113.10"))
        assertTrue(DohBypass.blocksTransport(UDP, 853, "203.0.113.10"))
        // DoH reached by address: 443 to a resolver IP.
        assertTrue(DohBypass.blocksTransport(TCP, 443, "8.8.8.8"))
        assertTrue(DohBypass.blocksTransport(UDP, 443, "1.1.1.1"))
        // Ordinary HTTPS to a normal host, and other ports.
        assertFalse(DohBypass.blocksTransport(TCP, 443, "203.0.113.10"))
        assertFalse(DohBypass.blocksTransport(TCP, 80, "1.1.1.1"))
        assertFalse(DohBypass.blocksTransport(UDP, 53, "1.1.1.1"))
        // Protocols without a stream/port meaning are never blocked here.
        assertFalse(DohBypass.blocksTransport(1, 853, "203.0.113.10"))
    }

    private companion object {
        const val TCP = 6
        const val UDP = 17
    }
}
