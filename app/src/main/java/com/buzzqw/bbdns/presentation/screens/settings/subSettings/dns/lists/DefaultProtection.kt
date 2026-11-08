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

import android.content.Context
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.Configuration
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState

/**
 * Activates the two protection categories everyone expects on a fresh install:
 * malware (Threat Intelligence Feed + StevenBlack Unified) and ads (HaGeZi
 * Multi NORMAL). It runs once, so a later manual disable is respected.
 */
object DefaultProtection {

    private const val PREFS = "bbdns_defaults"
    private const val KEY_PRESETS = "malware_ads_enabled_v1"

    fun ensureEnabledByDefault(context: Context, configuration: Configuration) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PRESETS, false)) return

        try {
            val enabledUrls = configuration.hosts.items
                .filter { it.state == HostState.DENY }
                .map { it.data }
                .toSet()

            val hasCore = enabledUrls.any { it in CORE_URLS }
            val hasTif = enabledUrls.any { it in TIF_URLS }
            val hasStevenBlack = StevenBlackCatalog.UNIFIED.url in enabledUrls

            // Activate each missing category independently, so a content-only
            // setup still gets the malware and ad defaults.
            val toAdd = buildList {
                if (!hasCore) add(HageziCatalog.NORMAL.url)
                if (!hasTif) add(HageziCatalog.TIF_MINI.url)
                if (!hasStevenBlack) add(StevenBlackCatalog.UNIFIED.url)
            }

            if (toAdd.isNotEmpty()) {
                toAdd.forEach { url ->
                    if (configuration.hosts.items.none { it.data == url }) {
                        configuration.addURL(url, url, HostState.DENY)
                    }
                }
                configuration.save()
            }
        } finally {
            prefs.edit().putBoolean(KEY_PRESETS, true).apply()
        }
    }

    private val TIF_URLS = setOf(
        HageziCatalog.TIF.url,
        HageziCatalog.TIF_MEDIUM.url,
        HageziCatalog.TIF_MINI.url,
    )

    private val CORE_URLS = HageziCatalog.all
        .filter { it.coreTier }
        .map { it.url }
        .toSet()
}
