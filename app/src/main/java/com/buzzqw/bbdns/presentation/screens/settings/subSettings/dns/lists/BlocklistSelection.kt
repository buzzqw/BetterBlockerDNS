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

import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostFile
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState

/**
 * Keeps the selected catalogue lists consistent, no matter how they were
 * enabled (protection profiles, presets, catalogue toggles or a restored
 * backup).
 *
 * HaGeZi ships several alternatives of the same list: Multi tiers, TIF
 * full/medium/mini, the DoH variants and the gambling variants. They must never
 * be enabled together, and a lower tier is redundant when a higher one is
 * already active. Only one entry per [BlocklistSource.exclusiveGroup] is kept,
 * preferring the highest-coverage variant.
 */
object BlocklistSelection {

    private val catalogue: List<BlocklistSource> = HageziCatalog.all + StevenBlackCatalog.all
    private val byUrl: Map<String, BlocklistSource> = catalogue.associateBy { it.url }

    /**
     * Removes exact duplicate URLs and redundant alternatives from [items].
     * Custom lists and disabled entries are left untouched.
     *
     * @return the removed entries, whose downloaded files can be deleted.
     */
    fun sanitize(items: MutableList<HostFile>): List<HostFile> {
        val removed = mutableListOf<HostFile>()

        // Duplicate URLs: keep the entry with the strongest state.
        val keepByData = items
            .groupBy { it.data }
            .mapValues { (_, entries) ->
                entries.maxByOrNull { statePriority(it.state) } ?: entries.first()
            }
        val duplicateIterator = items.iterator()
        while (duplicateIterator.hasNext()) {
            val item = duplicateIterator.next()
            if (item.data.isBlank()) continue
            val keep = keepByData[item.data]
            if (keep != null && item !== keep) {
                removed.add(item)
                duplicateIterator.remove()
            }
        }

        // Enabled alternatives: keep only the highest-coverage one of a group.
        val redundant = redundantEnabledUrls(
            items.filter { it.state == HostState.DENY }.map { it.data }
        )
        if (redundant.isNotEmpty()) {
            val conflictIterator = items.iterator()
            while (conflictIterator.hasNext()) {
                val item = conflictIterator.next()
                if (item.state == HostState.DENY && item.data in redundant) {
                    removed.add(item)
                    conflictIterator.remove()
                }
            }
        }
        return removed
    }

    /**
     * Enabled URLs that are redundant because another enabled list of the same
     * exclusive group has a higher or equal coverage rank.
     */
    fun redundantEnabledUrls(enabledUrls: Collection<String>): Set<String> {
        val groups = enabledUrls
            .distinct()
            .mapNotNull { byUrl[it] }
            .filter { it.exclusiveGroup != null }
            .groupBy { it.exclusiveGroup!! }

        return groups.values
            .filter { it.size > 1 }
            .flatMap { entries ->
                val keep = entries.maxByOrNull { BlocklistCoverage.rankOf(it.url) } ?: entries.first()
                entries.filter { it.url != keep.url }.map { it.url }
            }
            .toSet()
    }

    /**
     * URLs already present in the configuration that must be removed before
     * [urls] are enabled, because they are alternatives in the same group.
     */
    fun conflictsWhenEnabling(
        urls: Collection<String>,
        currentUrls: Collection<String>,
    ): Set<String> {
        val wantedGroups = urls
            .mapNotNull { byUrl[it] }
            .mapNotNull { it.exclusiveGroup }
            .toSet()
        if (wantedGroups.isEmpty()) return emptySet()

        val wanted = urls.toSet()
        return currentUrls
            .filter { it !in wanted }
            .filter { current ->
                val source = byUrl[current] ?: return@filter false
                source.exclusiveGroup != null && source.exclusiveGroup in wantedGroups
            }
            .toSet()
    }

    /**
     * Keeps at most one source per exclusive group, preferring the variant with
     * the highest coverage. Useful when a caller requests several alternatives
     * at once.
     */
    fun bestPerGroup(sources: List<BlocklistSource>): List<BlocklistSource> {
        val grouped = sources
            .filter { it.exclusiveGroup != null }
            .groupBy { it.exclusiveGroup!! }
        if (grouped.isEmpty()) return sources

        val keep = grouped.values
            .map { entries ->
                (entries.maxByOrNull { BlocklistCoverage.rankOf(it.url) } ?: entries.first()).url
            }
            .toSet()
        return sources.filter { it.exclusiveGroup == null || it.url in keep }
    }

    private fun statePriority(state: HostState): Int = when (state) {
        HostState.DENY -> 3
        HostState.ALLOW -> 2
        HostState.IGNORE -> 1
    }
}
