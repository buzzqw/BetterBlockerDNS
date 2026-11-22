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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs

import com.buzzqw.bbdns.domain.model.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Turns the query log into CSV, for a user who wants to check the filter
 * outside the app: which names were let through, which were blocked, and by
 * which app.
 *
 * Plain Kotlin with the clock injected, so the format (and the escaping that
 * makes it readable by a spreadsheet) is covered by unit tests instead of by
 * the next bug report.
 */
object LogCsv {

    const val MIME = "text/csv"

    const val HEADER = "time,app,uid,destination,port,protocol,status"

    /**
     * @param appLabel resolves a uid to a readable app name; the caller
     *   caches it, because a log can hold thousands of rows.
     */
    fun export(
        logs: List<Log>,
        appLabel: (Int) -> String,
        timeFormatter: (Long) -> String = ::defaultTimestamp,
    ): String {
        val builder = StringBuilder(HEADER).append('\n')
        val labels = HashMap<Int, String>()
        for (log in logs) {
            val app = labels.getOrPut(log.packageID) { appLabel(log.packageID) }
            builder.append(escape(timeFormatter(log.time))).append(',')
                .append(escape(app)).append(',')
                .append(log.packageID).append(',')
                .append(escape(log.destinationAddress ?: log.destinationIP)).append(',')
                .append(escape(log.destinationPort)).append(',')
                .append(escape(log.protocol)).append(',')
                .append(escape(log.packetStatus.name)).append('\n')
        }
        return builder.toString()
    }

    /** ISO-8601 with the device offset, e.g. 2026-11-22T09:23:19+01:00. */
    fun defaultTimestamp(epochMillis: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        format.timeZone = TimeZone.getDefault()
        return format.format(Date(epochMillis))
    }

    /**
     * A field is quoted only when it has to be: commas, quotes and line breaks
     * are the three things that break a CSV. Double quotes are doubled, as
     * RFC 4180 wants.
     */
    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuotes) return value
        return buildString(value.length + 2) {
            append('"')
            for (char in value) {
                if (char == '"') append("\"\"") else append(char)
            }
            append('"')
        }
    }
}
