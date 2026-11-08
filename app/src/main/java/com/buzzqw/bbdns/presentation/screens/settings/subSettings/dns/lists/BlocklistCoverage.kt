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

/**
 * Detects when a blocklist is already contained in a more complete list of the
 * same family (for example Multi NORMAL inside Multi PRO, or a StevenBlack
 * extension inside Unified). Used to avoid downloading the same domains twice
 * when the user activates a smaller list after a protection profile.
 */
object BlocklistCoverage {

    private val coreIds = setOf(
        HageziCatalog.LIGHT.id,
        HageziCatalog.NORMAL.id,
        HageziCatalog.PRO.id,
        HageziCatalog.PRO_MINI.id,
        HageziCatalog.PRO_PLUS.id,
        HageziCatalog.PRO_PLUS_MINI.id,
        HageziCatalog.ULTIMATE.id,
        HageziCatalog.ULTIMATE_MINI.id,
    )

    private val tifIds = setOf(
        HageziCatalog.TIF_MINI.id,
        HageziCatalog.TIF_MEDIUM.id,
        HageziCatalog.TIF.id,
    )

    /** Higher tiers contain the lower ones of the same family. */
    private val ranks: Map<String, Int> = buildMap {
        put(HageziCatalog.LIGHT.id, 10)
        put(HageziCatalog.NORMAL.id, 20)
        put(HageziCatalog.PRO.id, 30)
        put(HageziCatalog.PRO_MINI.id, 30)
        put(HageziCatalog.PRO_PLUS.id, 40)
        put(HageziCatalog.PRO_PLUS_MINI.id, 40)
        put(HageziCatalog.ULTIMATE.id, 50)
        put(HageziCatalog.ULTIMATE_MINI.id, 50)
        put(HageziCatalog.TIF_MINI.id, 10)
        put(HageziCatalog.TIF_MEDIUM.id, 20)
        put(HageziCatalog.TIF.id, 30)
        put(StevenBlackCatalog.UNIFIED.id, 20)
        StevenBlackCatalog.all
            .filter { it.id != StevenBlackCatalog.UNIFIED.id }
            .forEach { put(it.id, 10) }
    }

    private val all: List<BlocklistSource> = HageziCatalog.all + StevenBlackCatalog.all

    fun idForUrl(url: String): String? = all.firstOrNull { it.url == url }?.id

    fun nameForUrl(url: String): String? = all.firstOrNull { it.url == url }?.name

    /**
     * The name of the enabled list that already contains [url], or null when
     * the list is not covered.
     */
    fun coveringName(url: String, enabledUrls: Set<String>): String? {
        val id = idForUrl(url) ?: return null
        val family = familyOf(id) ?: return null
        val rank = ranks[id] ?: return null
        val coveringId = enabledUrls
            .mapNotNull { enabled ->
                val otherId = idForUrl(enabled) ?: return@mapNotNull null
                if (familyOf(otherId) != family) return@mapNotNull null
                val otherRank = ranks[otherId] ?: return@mapNotNull null
                if (otherRank > rank) otherId to otherRank else null
            }
            .maxByOrNull { it.second }
            ?.first
            ?: return null
        return all.firstOrNull { it.id == coveringId }?.name
    }

    fun isCovered(url: String, enabledUrls: Set<String>): Boolean =
        coveringName(url, enabledUrls) != null

    private fun familyOf(id: String): String? = when {
        id in coreIds -> "core"
        id in tifIds -> "tif"
        all.any { it.id == id && it is StevenBlackList } -> "stevenblack"
        else -> null
    }
}
