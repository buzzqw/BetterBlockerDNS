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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityAllowlistTest {

    @Test
    fun `entries are valid suffixes`() {
        assertTrue(CompatibilityAllowlist.domains.isNotEmpty())
        CompatibilityAllowlist.domains.forEach { domain ->
            assertEquals(domain, domain.lowercase())
            assertFalse(domain, domain.startsWith("*") || domain.contains("/"))
            assertTrue(domain, domain.contains("."))
            assertFalse(domain, domain.startsWith(".") || domain.endsWith("."))
        }
    }

    @Test
    fun `entries are unique and sorted`() {
        val domains = CompatibilityAllowlist.domains
        assertEquals(domains.distinct(), domains)
        assertEquals(domains.sorted(), domains)
    }

    @Test
    fun `covers the usual breakage`() {
        listOf(
            "paypal.com",
            "poste.it",
            "intesasanpaolo.com",
            "inps.it",
            "fcm.googleapis.com",
            "connectivitycheck.gstatic.com"
        ).forEach { domain ->
            assertTrue(domain, domain in CompatibilityAllowlist.domains)
        }
    }
}
