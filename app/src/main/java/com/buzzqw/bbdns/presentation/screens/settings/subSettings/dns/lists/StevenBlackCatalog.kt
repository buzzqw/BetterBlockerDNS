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

enum class StevenBlackCategory(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    UNIFIED(
        R.string.catalog_stevenblack_category_unified_title,
        R.string.catalog_stevenblack_category_unified_desc
    ),
    CONTENT(
        R.string.catalog_stevenblack_category_content_title,
        R.string.catalog_stevenblack_category_content_desc
    )
}

data class StevenBlackList(
    override val id: String,
    override val name: String,
    override val description: String,
    @StringRes override val descriptionRes: Int? = null,
    val path: String,
    val category: StevenBlackCategory,
    override val recommended: Boolean = false
) : BlocklistSource {
    override val url: String get() = "$BASE_URL/$path"

    companion object {
        const val BASE_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master"
    }
}

object StevenBlackCatalog {

    val UNIFIED = StevenBlackList(
        id = "stevenblack",
        name = "StevenBlack Unified",
        description = "Unified hosts file with ads, trackers and malware domains. The safest starting point of this catalogue.",
        descriptionRes = R.string.catalog_stevenblack_unified_desc,
        path = "hosts",
        category = StevenBlackCategory.UNIFIED,
        recommended = true
    )

    // The "-only" builds contain just the extension domains, so they can be
    // combined freely without downloading the Unified list several times
    // (the plain "alternates/<ext>/hosts" files already embed Unified).

    val FAKENEWS = StevenBlackList(
        id = "stevenblack.fakenews",
        name = "StevenBlack Fakenews",
        description = "Fake-news domains only. Combine with Unified or any other list.",
        descriptionRes = R.string.catalog_stevenblack_fakenews_desc,
        path = "alternates/fakenews-only/hosts",
        category = StevenBlackCategory.CONTENT
    )

    val GAMBLING = StevenBlackList(
        id = "stevenblack.gambling",
        name = "StevenBlack Gambling",
        description = "Gambling and betting domains only. Combine with Unified or any other list.",
        descriptionRes = R.string.catalog_stevenblack_gambling_desc,
        path = "alternates/gambling-only/hosts",
        category = StevenBlackCategory.CONTENT
    )

    val PORN = StevenBlackList(
        id = "stevenblack.porn",
        name = "StevenBlack Porn",
        description = "Adult content domains only. Combine with Unified or any other list.",
        descriptionRes = R.string.catalog_stevenblack_porn_desc,
        path = "alternates/porn-only/hosts",
        category = StevenBlackCategory.CONTENT
    )

    val SOCIAL = StevenBlackList(
        id = "stevenblack.social",
        name = "StevenBlack Social",
        description = "Social network domains only. Combine with Unified or any other list.",
        descriptionRes = R.string.catalog_stevenblack_social_desc,
        path = "alternates/social-only/hosts",
        category = StevenBlackCategory.CONTENT
    )

    val extensions: List<StevenBlackList> = listOf(FAKENEWS, GAMBLING, PORN, SOCIAL)

    val all: List<StevenBlackList> = listOf(UNIFIED) + extensions

    val byId: Map<String, StevenBlackList> = all.associateBy { it.id }

    private val extensionByKey: Map<String, StevenBlackList> = mapOf(
        "fakenews" to FAKENEWS,
        "gambling" to GAMBLING,
        "porn" to PORN,
        "social" to SOCIAL,
    )

    /**
     * Earlier releases used "alternates/<ext1>-<ext2>/hosts" files, which
     * embed Unified. Returns the equivalent stackable selection (Unified plus
     * the "-only" extensions) for such a URL, or null if it is not legacy.
     */
    fun legacyReplacement(url: String): List<StevenBlackList>? {
        val prefix = "${StevenBlackList.BASE_URL}/alternates/"
        if (!url.startsWith(prefix) || !url.endsWith("/hosts")) return null
        val folder = url.removePrefix(prefix).removeSuffix("/hosts")
        if (folder.endsWith("-only")) return null
        val parts = folder.split("-")
        val exts = parts.map { extensionByKey[it] ?: return null }
        return listOf(UNIFIED) + exts
    }
}
