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
import org.junit.Test

class TemporaryDnsAllowTest {

    @After
    fun tearDown() {
        TemporaryDnsAllow.clear()
    }

    @Test
    fun `an allowed domain is allowed until it expires`() {
        TemporaryDnsAllow.allow("example.com", minutes = 5, now = 0)
        assertTrue(TemporaryDnsAllow.isAllowed("example.com", now = 4 * 60_000))
        assertFalse(TemporaryDnsAllow.isAllowed("example.com", now = 5 * 60_000 + 1))
    }

    @Test
    fun `a parent domain covers its subdomains`() {
        TemporaryDnsAllow.allow("example.com", minutes = 5, now = 0)
        assertTrue(TemporaryDnsAllow.isAllowed("www.example.com", now = 1_000))
        assertTrue(TemporaryDnsAllow.isAllowed("a.b.example.com", now = 1_000))
        assertFalse(TemporaryDnsAllow.isAllowed("example.net", now = 1_000))
        assertFalse(TemporaryDnsAllow.isAllowed("notexample.com", now = 1_000))
    }

    @Test
    fun `case and trailing dots are ignored`() {
        TemporaryDnsAllow.allow("Example.COM.", minutes = 5, now = 0)
        assertTrue(TemporaryDnsAllow.isAllowed("example.com", now = 1))
    }

    @Test
    fun `a bare tld can never be allowed as a parent match`() {
        TemporaryDnsAllow.allow("com", minutes = 5, now = 0)
        assertFalse(TemporaryDnsAllow.isAllowed("example.com", now = 1))
    }

    @Test
    fun `stopping protection clears every choice`() {
        TemporaryDnsAllow.allow("example.com", minutes = 5, now = 0)
        TemporaryDnsAllow.clear()
        assertFalse(TemporaryDnsAllow.isAllowed("example.com", now = 1))
        assertTrue(TemporaryDnsAllow.size() == 0)
    }

    @Test
    fun `an invalid duration is ignored`() {
        TemporaryDnsAllow.allow("", minutes = 5, now = 0)
        TemporaryDnsAllow.allow("example.com", minutes = 0, now = 0)
        assertTrue(TemporaryDnsAllow.size() == 0)
    }
}
