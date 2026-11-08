/*
 * Copyright (C) 2025-2026 Vexzure
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

package com.buzzqw.bbdns.core.utils.constants

object ProjectConstants {
    const val APP_NAME = "BetterBlockerDNS"
    const val APP_SHORT_NAME = "BBDNS"

    // Kept for compatibility with the upstream signature verification screen.
    // Release builds of BBDNS should add their own signing certificate hash here.
    val SHA_256_SIGNING = setOf(
        "3dac74ea57cbf9d5dd10ac2f443036afd38581588dd4dafa02d230bddb4250dd",
        "f8388448de6d3eb4831a60aa4194a0524cbb443fe5806090c1a403565f49a06b",
        "628704bf1c86130eff49ad991c1b4dc9006700112e5ebe692da3da43f074d8fd"
    )
    const val DEVELOPER = "buzzqw"

    const val PROJECT_DOWNLOADS = "https://github.com/buzzqw/BetterBlockerDNS"
    const val PROJECT_SOURCE_CODE = "https://github.com/buzzqw/BetterBlockerDNS"
    const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.en.html"
    const val SUPPORT_MAIL = "azanzani@gmail.com"
    const val SUPPORT_DISCORD = "https://github.com/buzzqw/BetterBlockerDNS/discussions"
    const val SUPPORT_INSTAGRAM = "https://github.com/buzzqw/BetterBlockerDNS"
    const val GITHUB_FEATURE_REQUEST = "https://github.com/buzzqw/BetterBlockerDNS/issues"

    const val HAGEZI_REPO = "https://github.com/hagezi/dns-blocklists"
    const val HAGEZI_LICENSE = "https://github.com/hagezi/dns-blocklists/blob/main/LICENSE"

    const val STEVENBLACK_REPO = "https://github.com/StevenBlack/hosts"
    const val STEVENBLACK_LICENSE = "https://github.com/StevenBlack/hosts/blob/master/license.txt"

    const val PLAY_STORE_URL = "market://details?id=com.buzzqw.bbdns"
    const val PLAY_STORE_WEB_URL = "https://play.google.com/store/apps/details?id=com.buzzqw.bbdns"
}
