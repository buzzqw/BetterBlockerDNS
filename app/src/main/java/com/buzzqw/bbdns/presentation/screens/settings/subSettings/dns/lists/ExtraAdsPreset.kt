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

import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.Configuration
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState

/**
 * Optional list bundled with the app.
 *
 * The default setup (HaGeZi Multi NORMAL plus StevenBlack Unified) already
 * covers the main advertising, tracking and YouTube ad-telemetry domains.
 * This list fills the gaps: mobile ad networks and measurement endpoints that
 * the default tiers do not include.
 *
 * The file is shipped in the APK, so the switch works offline and there is no
 * third-party URL to keep alive. The `-onlydomains` suffix makes the rule
 * database treat every entry as a suffix, so a base domain also covers its
 * subdomains.
 *
 * It cannot remove YouTube video ads: pre-roll and mid-roll ads are delivered
 * from the same hosts as the videos and DNS cannot distinguish them.
 */
object ExtraAdsPreset {

    const val URL = "asset:///blocklists/extra-ads-onlydomains.txt"

    /** Stable title used in the config file and in the active lists screen. */
    const val TITLE = "BBDNS Extra ads"

    fun isEnabled(configuration: Configuration): Boolean =
        configuration.hosts.items.any { it.data == URL && it.state == HostState.DENY }

    /**
     * Adds or removes the bundled list from the active configuration.
     * The caller saves the configuration and refreshes the rule database.
     */
    fun setEnabled(configuration: Configuration, enabled: Boolean, title: String = TITLE) {
        configuration.hosts.items.removeAll { it.data == URL }
        if (enabled) {
            configuration.addURL(title, URL, HostState.DENY)
        }
    }
}
