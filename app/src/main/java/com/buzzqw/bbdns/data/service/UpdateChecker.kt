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

package com.buzzqw.bbdns.data.service

import android.content.Context
import com.buzzqw.bbdns.BuildConfig
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.core.utils.VersionComparator
import com.buzzqw.bbdns.core.utils.constants.ProjectConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks GitHub for the latest release and keeps the answer for the banner in
 * Settings. This is the only request BBDNS makes that is not a DNS query or a
 * list download: it runs at most once a day, only when the app is opened, and
 * it carries nothing but the request itself (no identifier, no phone data).
 *
 * Play builds receive updates from Play; the banner still points to GitHub
 * because both channels are signed with the same key, so either build can be
 * installed over the other.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext context: Context,
) {

    data class Status(
        val checking: Boolean = false,
        val latestVersion: String? = null,
        val releaseUrl: String? = null,
        val updateAvailable: Boolean = false,
        val checkedAt: Long = 0L,
        val failed: Boolean = false,
    )

    private val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val _status = MutableStateFlow(restore())
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Called when the app starts: at most one request per [CHECK_INTERVAL_MS]. */
    fun checkOnceADay() = check(force = false)

    /** Called from Settings: ignores the daily limit. */
    fun checkNow() = check(force = true)

    private fun check(force: Boolean) {
        val current = _status.value
        if (current.checking) return
        if (!force && System.currentTimeMillis() - current.checkedAt < CHECK_INTERVAL_MS) return

        _status.value = current.copy(checking = true, failed = false)
        scope.launch {
            try {
                val (version, url) = fetchLatestRelease()
                val checkedAt = System.currentTimeMillis()
                prefs.edit()
                    .putString(KEY_LATEST_VERSION, version)
                    .putString(KEY_RELEASE_URL, url)
                    .putLong(KEY_CHECKED_AT, checkedAt)
                    .apply()
                _status.value = Status(
                    checking = false,
                    latestVersion = version,
                    releaseUrl = url,
                    updateAvailable = VersionComparator.isNewer(version, BuildConfig.VERSION_NAME),
                    checkedAt = checkedAt,
                )
            } catch (e: Exception) {
                Logger.warn("Update check failed: ${e.message}")
                // Remember the attempt so an offline phone does not retry on
                // every launch, but keep the last version that was found.
                val checkedAt = System.currentTimeMillis()
                prefs.edit().putLong(KEY_CHECKED_AT, checkedAt).apply()
                _status.value = _status.value.copy(
                    checking = false,
                    checkedAt = checkedAt,
                    failed = true,
                )
            }
        }
    }

    private fun fetchLatestRelease(): Pair<String, String> {
        val request = Request.Builder()
            .url(LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "BetterBlockerDNS/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub replied ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("empty response")
            val json = JSONObject(body)
            val version = json.optString("tag_name").takeIf { it.isNotBlank() }
                ?: throw IOException("release without a tag")
            val url = json.optString("html_url").takeIf { it.isNotBlank() }
                ?: ProjectConstants.PROJECT_DOWNLOADS
            return version to url
        }
    }

    private fun restore(): Status {
        val latest = prefs.getString(KEY_LATEST_VERSION, null)
        return Status(
            latestVersion = latest,
            releaseUrl = prefs.getString(KEY_RELEASE_URL, null),
            updateAvailable = latest != null &&
                VersionComparator.isNewer(latest, BuildConfig.VERSION_NAME),
            checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L),
        )
    }

    companion object {
        private const val LATEST_RELEASE_API =
            "https://api.github.com/repos/buzzqw/BetterBlockerDNS/releases/latest"
        private const val PREFERENCES = "bbdns_update_check"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_RELEASE_URL = "release_url"
        private const val KEY_CHECKED_AT = "checked_at"
        private const val TIMEOUT_SECONDS = 10L
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
