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
import com.buzzqw.bbdns.R

/**
 * Catalogues that are not HaGeZi but are still first-class sources, because a
 * protection profile uses them.
 *
 * The AdGuard DNS filter is compiled for DNS blockers: it is a list of plain
 * hostnames with no element hiding or path rules, about 190,000 entries, and it
 * is the list that adds the most on top of HaGeZi and StevenBlack.
 */
object AdguardCatalog {

    data class AdguardList(
        override val id: String,
        override val name: String,
        override val description: String,
        @StringRes override val descriptionRes: Int? = null,
        override val url: String,
        override val recommended: Boolean = false,
    ) : BlocklistSource

    val DNS_FILTER = AdguardList(
        id = "adguard.dns",
        name = "AdGuard DNS filter",
        description = "Native DNS list with about 190,000 ad and tracker domains. " +
            "It is the companion of every protection profile from Balanced upwards.",
        descriptionRes = R.string.catalog_adguard_dns_desc,
        url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt",
        recommended = true,
    )

    /** Every entry, in display order. */
    val all: List<AdguardList> = listOf(DNS_FILTER)

    val byId: Map<String, AdguardList> = all.associateBy { it.id }

    val byUrl: Map<String, AdguardList> = all.associateBy { it.url }
}
