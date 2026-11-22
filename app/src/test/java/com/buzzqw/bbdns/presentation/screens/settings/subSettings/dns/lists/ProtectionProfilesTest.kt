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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The protection profiles are the one-tap setup, so every list they name must
 * resolve to a real source: an id that no catalogue knows is silently skipped
 * and the profile quietly protects less than it says.
 */
class ProtectionProfilesTest {

    @Test
    fun `every list a profile names exists in a catalogue`() {
        val missing = HageziCatalog.profiles.flatMap { profile ->
            profile.allListIds.filterNot { it in HageziCatalog.profileSources }
                .map { "${profile.id}: $it" }
        }
        assertEquals(emptyList<String>(), missing)
    }

    /** A profile must download the malware base, not assume it was there. */
    @Test
    fun `profiles from balanced upwards load StevenBlack Unified`() {
        val ids = listOf("balanced", "pro", "aggressive", "maximum")
        ids.forEach { id ->
            val profile = HageziCatalog.profiles.first { it.id == id }
            assertTrue(
                "$id must include StevenBlack Unified",
                StevenBlackCatalog.UNIFIED.id in profile.allListIds,
            )
            assertTrue(
                "$id must include the AdGuard DNS filter",
                AdguardCatalog.DNS_FILTER.id in profile.allListIds,
            )
        }
    }

    @Test
    fun `essential stays on its two HaGeZi lists`() {
        val essential = HageziCatalog.profiles.first { it.id == "essential" }
        assertEquals(2, essential.allListIds.size)
        assertFalse(StevenBlackCatalog.UNIFIED.id in essential.allListIds)
    }

    /**
     * Profile application removes the lists it owns before adding its own.
     * StevenBlack is added by the profiles but not owned by them, so a manual
     * choice for it survives a profile switch.
     */
    @Test
    fun `StevenBlack is not owned by a profile`() {
        assertFalse(StevenBlackCatalog.UNIFIED.id in HageziCatalog.profileManagedIds)
        assertTrue(AdguardCatalog.DNS_FILTER.id in HageziCatalog.profileManagedIds)
    }

    @Test
    fun `each profile is identified by its own HaGeZi lists`() {
        HageziCatalog.profiles.forEach { profile ->
            val ids = profile.listIds.mapNotNull { id ->
                HageziCatalog.profileSources[id]?.url
            }
            assertEquals(profile.listIds.size, ids.size)
            assertNotNull(profile.allListIds.firstOrNull { it in HageziCatalog.profileManagedIds })
        }
    }
}
