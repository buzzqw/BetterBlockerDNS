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

package com.buzzqw.bbdns.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTest {

    @Test
    fun `parses a comma separated package list`() {
        assertEquals(emptySet<String>(), parsePackageList(""))
        assertEquals(setOf("a.b", "c.d"), parsePackageList(" a.b , c.d ,, a.b "))
    }

    @Test
    fun `serialises a package list deterministically`() {
        assertEquals("", serializePackageList(emptyList()))
        assertEquals("a.b,c.d", serializePackageList(listOf("c.d", "a.b", "c.d")))
    }
}
