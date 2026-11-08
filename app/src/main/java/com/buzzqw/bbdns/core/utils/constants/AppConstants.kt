/*
 * Copyright (C) 2025-2026 Vexzure
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

package com.buzzqw.bbdns.core.utils.constants

import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.HageziCatalog
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.StevenBlackCatalog


object AppConstants {
    object AnimationConstants {
        const val FADE_DURATION: Int = 200
        const val SCALE_DURATION: Int = 250
        const val SLIDE_DURATION: Int = 300
        const val INITIAL_SCALE: Float = 0.95f
    }

    object DatabaseConstants {
        const val DATABASE_NAME: String = "packages"
        const val DATABASE_VERSION: Int = 9
        const val SETTINGS_DATABASE_NAME: String = "settings"
    }

    object NetworkConstants {
        const val NFLOG_GROUP_ID: Int = 58
    }

    object DnsBlockLists {
        /*
         * The filtering presets reuse the native HaGeZi catalogue entries
         * (Settings > Blocklist sources): URLs are never duplicated here.
         */

        // Old blocklist URLs (for migration from earlier releases)
        object Legacy {
            val MALWARE_PROTECTION = listOf(
                "https://raw.githubusercontent.com/DandelionSprout/adfilt/master/Alternate%20versions%20Anti-Malware%20List/AntiMalwareHosts.txt",
            )
            val AD_PROTECTION = listOf(
                "https://adaway.org/hosts.txt",
                "https://small.oisd.nl",
            )
            val ADS_EXTREME = listOf(
                "https://blokada.org/mirror/v5/goodbyeads/standard/hosts.txt",
            )
            val PRIVACY_PROTECTION = listOf(
                "https://hostfiles.frogeye.fr/firstparty-trackers-hosts.txt",
                "https://raw.githubusercontent.com/Perflyst/PiHoleBlocklist/master/android-tracking.txt",
            )
            val GAMBLING_PROTECTION = listOf(
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/gambling-only/hosts",
            )
            val ADULT_PROTECTION = listOf(
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
                "https://raw.githubusercontent.com/blocklistproject/Lists/master/porn.txt",
            )
            val SOCIAL_PROTECTION = listOf(
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/social-only/hosts",
            )
        }

        val MALWARE_PROTECTION = listOf(
            // HaGeZi Threat Intelligence Feeds: the recommended malware/scam feed.
            HageziCatalog.TIF.url,
            // StevenBlack Unified adds the classic, battle-tested malware set.
            StevenBlackCatalog.UNIFIED.url,
        )
        val AD_PROTECTION = listOf(
            // HaGeZi Multi NORMAL: all-round ads, trackers, phishing and malware.
            HageziCatalog.NORMAL.url,
        )
        val ADS_EXTREME = listOf(
            // HaGeZi Multi PRO++: aggressive ad filtering.
            HageziCatalog.PRO_PLUS.url,
        )
        val PRIVACY_PROTECTION = listOf(
            // HaGeZi Multi PRO: telemetry and tracker coverage.
            HageziCatalog.PRO.url,
        )
        val GAMBLING_PROTECTION = listOf(
            HageziCatalog.GAMBLING.url,
        )
        val ADULT_PROTECTION = listOf(
            HageziCatalog.NSFW.url,
        )
        val SOCIAL_PROTECTION = listOf(
            HageziCatalog.SOCIAL.url,
        )

        /** Every Multi tier; used to reflect "ads are blocked" in the UI. */
        val CORE_MULTI = HageziCatalog.all.filter { it.coreTier }.map { it.url }

        /** Every TIF variant plus StevenBlack Unified; the malware category. */
        val TIF_ALL = HageziCatalog.all.filter { it.threatFeed }.map { it.url } +
            StevenBlackCatalog.UNIFIED.url

        /** Tiers that also provide the tracker/privacy category. */
        val PRIVACY_ALL = listOf(
            HageziCatalog.PRO,
            HageziCatalog.PRO_MINI,
            HageziCatalog.PRO_PLUS,
            HageziCatalog.PRO_PLUS_MINI,
            HageziCatalog.ULTIMATE,
            HageziCatalog.ULTIMATE_MINI,
        ).map { it.url }

        /** Tiers that provide the aggressive ad category. */
        val ADS_EXTREME_ALL = listOf(
            HageziCatalog.PRO_PLUS,
            HageziCatalog.PRO_PLUS_MINI,
            HageziCatalog.ULTIMATE,
            HageziCatalog.ULTIMATE_MINI,
        ).map { it.url }
    }

    object Links {
        const val TRANSLATE = "https://github.com/buzzqw/BetterBlockerDNS"
    }
}