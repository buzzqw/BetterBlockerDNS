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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns

import android.content.Context

/**
 * Remembers when a blocklist update last completed successfully, so the
 * auto-update setting can show it. Both automatic and manual updates count.
 */
object BlocklistUpdateStatus {

    private const val PREFS = "bbdns_blocklists"
    private const val KEY_LAST_UPDATE = "last_update_time"

    fun recordSuccess(context: Context, timestamp: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_UPDATE, timestamp)
            .apply()
    }

    /** Epoch milliseconds of the last successful update, or null if none yet. */
    fun lastUpdate(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE, 0L)
            .takeIf { it > 0L }
}
