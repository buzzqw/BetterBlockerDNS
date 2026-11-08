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

import android.net.Uri

/** Human-readable names for blocklists, whose config title is often just the URL. */
object BlocklistNames {

    private val byUrl: Map<String, BlocklistSource> by lazy {
        (HageziCatalog.all + StevenBlackCatalog.all).associateBy { it.url }
    }

    /** Catalogue name when known, otherwise the title or a short form of the URL. */
    fun displayName(title: String, data: String = title): String {
        byUrl[data]?.let { return it.name }
        byUrl[title]?.let { return it.name }
        if (title.isNotBlank() && !title.contains("://")) return title
        return shortUrl(data.ifBlank { title })
    }

    /** "host › file" for a URL, the input otherwise. */
    fun shortUrl(value: String): String = try {
        val uri = Uri.parse(value)
        val host = uri.host?.removePrefix("www.")
        val last = uri.lastPathSegment?.takeIf { it.isNotBlank() }
        when {
            host == null -> value
            last == null -> host
            else -> "$host › $last"
        }
    } catch (_: Exception) {
        value
    }
}
