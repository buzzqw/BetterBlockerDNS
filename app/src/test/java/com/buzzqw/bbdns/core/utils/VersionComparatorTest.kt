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

package com.buzzqw.bbdns.core.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `detects a newer patch release`() {
        assertTrue(VersionComparator.isNewer("1.0.55", "1.0.54"))
        assertTrue(VersionComparator.isNewer("v1.0.55", "1.0.54"))
        assertFalse(VersionComparator.isNewer("1.0.54", "1.0.55"))
    }

    @Test
    fun `compares each part as a number`() {
        assertTrue(VersionComparator.isNewer("1.0.10", "1.0.9"))
        assertTrue(VersionComparator.isNewer("1.1.0", "1.0.99"))
        assertFalse(VersionComparator.isNewer("1.0.9", "1.0.10"))
    }

    @Test
    fun `equal versions are not an update`() {
        assertFalse(VersionComparator.isNewer("1.0.55", "1.0.55"))
        assertFalse(VersionComparator.isNewer("v1.0.55", "1.0.55"))
        assertFalse(VersionComparator.isNewer("1.0.55", "v1.0.55"))
    }

    @Test
    fun `missing parts count as zero`() {
        assertTrue(VersionComparator.isNewer("1.1", "1.0.9"))
        assertFalse(VersionComparator.isNewer("1.0", "1.0.0"))
    }

    @Test
    fun `unexpected text does not crash the check`() {
        assertFalse(VersionComparator.isNewer("", "1.0.55"))
        assertTrue(VersionComparator.isNewer("1.0.55-beta", "1.0.54"))
    }
}
