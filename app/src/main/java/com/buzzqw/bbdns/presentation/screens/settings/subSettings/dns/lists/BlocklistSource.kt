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

import androidx.annotation.StringRes

/**
 * Common contract for a downloadable blocklist exposed by the in-app catalogue.
 * Both HaGeZi and StevenBlack entries implement it, so the DNS layer can treat
 * them uniformly.
 */
interface BlocklistSource {
    val id: String
    val name: String
    /** English fallback; the UI shows [descriptionRes] when available. */
    val description: String
    @get:StringRes val descriptionRes: Int? get() = null
    val url: String
    val recommended: Boolean

    /**
     * Lists in the same group are alternatives (for example the HaGeZi Multi
     * tiers or the full/medium/mini variants of a list): enabling one
     * disables the others. Null means the list can be freely combined.
     */
    val exclusiveGroup: String? get() = null
}
