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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPersistenceTest {

    /**
     * Settings are saved by reflection over the declared fields, so a field of
     * a type the store does not handle is silently not saved. Nothing in the
     * compiler catches that; this does.
     */
    @Test
    fun `every settings field has a type the store can persist`() {
        val supported = setOf(
            Boolean::class.java,
            String::class.java,
            Int::class.java,
            Float::class.java,
            Long::class.java,
        )
        val unsupported = Settings::class.java.declaredFields
            .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .filterNot { it.name.startsWith("$") }
            .filter { it.type !in supported }
            .map { "${it.name}: ${it.type.simpleName}" }
        assertEquals(emptyList<String>(), unsupported)
    }

    /**
     * A backup written by an older version must keep loading: the new field
     * falls back to its default instead of failing the import.
     */
    @Test
    fun `a backup written before the notice page existed still loads`() {
        val current = SettingsBackup(
            automaticTheme = true,
            darkTheme = false,
            useDynamicIcons = false,
            dynamicTheme = false,
            amoledTheme = false,
            customColor = -14975141,
            screenProtection = false,
            blockPort80 = false,
            logs = true,
            detailedLogs = false,
            showSystemPackages = false,
            showOfflinePackages = false,
            wiFiDefault = true,
            cellularDefault = true,
            startOnBoot = true,
            allowLocal = true,
            blockWifiWhenScreenOff = false,
            blockCellularWhenScreenOff = false,
            permanentNotification = false,
            networkSpeedMonitor = false,
            sendNotificationOnInstall = false,
            malwareProtection = false,
            adBlocker = false,
            trackerProtection = false,
            autoUpdateInterval = 24 * 60 * 60 * 1000L,
            dohEnabled = true,
            dohUrl = "https://cloudflare-dns.com/dns-query",
            blockDohBypass = true,
            safeSearch = true,
            localBlockPage = true,
        )

        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToJsonElement(SettingsBackup.serializer(), current).jsonObject
        val older = JsonObject(encoded.toMutableMap().also { it.remove("localBlockPage") })

        val decoded = json.decodeFromJsonElement(SettingsBackup.serializer(), older)
        assertFalse(decoded.localBlockPage)
        // The rest of the backup is untouched.
        assertTrue(decoded.safeSearch)
        assertTrue(decoded.blockDohBypass)
        assertEquals(current.dohUrl, decoded.dohUrl)
    }
}
