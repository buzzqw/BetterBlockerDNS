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

package com.buzzqw.bbdns.service.firewall.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import com.buzzqw.bbdns.App
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.data.repository.SettingsRepositoryImpl
import com.buzzqw.bbdns.domain.model.parsePackageList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * UIDs of the apps whose DNS queries skip the blocklists
 * (Settings.dnsFilterExcludedPackages). The apps stay inside the VPN.
 *
 * The set is refreshed in two ways, so the running VPN never needs a restart:
 * - the detail screen calls [refresh] right after saving the setting;
 * - [isExcluded] re-reads the settings in the background at most every
 *   [REFRESH_INTERVAL_MS], which also picks up reinstalled apps (new UID),
 *   restored backups and changes made from other screens.
 *
 * Limitations of the UID matching:
 * - the UID of a DNS packet comes from ConnectivityManager.getConnectionOwnerUid
 *   (resolved by AppRule, Android 10+). Android's resolver (netd) marks its
 *   sockets with the requesting app's UID, so queries are normally attributed
 *   to the app. On Android 9 and older the /proc/net/udp fallback often cannot
 *   find the socket or reports a system UID (root, system, dns), and then the
 *   exclusion does not apply;
 * - netd caches answers per network for every app: if another app has just
 *   received a blocked answer for a domain, an excluded app may get that cached
 *   answer without any packet reaching the VPN (and vice versa);
 * - apps sharing a UID (android:sharedUserId) are excluded together.
 */
object DnsFilterExclusions {
    private const val REFRESH_INTERVAL_MS = 15_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshing = AtomicBoolean(false)

    @Volatile
    private var excludedUids: Set<Int> = emptySet()

    @Volatile
    private var lastRefresh = 0L

    fun isExcluded(uid: Int): Boolean {
        refreshIfStale()
        val uids = excludedUids
        return uid > 0 && uids.isNotEmpty() && uid in uids
    }

    /** Applies a new comma-separated package list (called after saving the setting). */
    fun refresh(context: Context, packages: String) {
        val appContext = context.applicationContext
        scope.launch { apply(appContext, packages) }
    }

    private fun refreshIfStale() {
        if (SystemClock.elapsedRealtime() - lastRefresh < REFRESH_INTERVAL_MS) return
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                val context = App.applicationContext
                val settings = SettingsRepositoryImpl(context).loadSettings()
                apply(context, settings.dnsFilterExcludedPackages)
            } catch (e: Exception) {
                Logger.error("DNS filter exclusions: cannot load settings: ${e.message}")
                lastRefresh = SystemClock.elapsedRealtime()
            } finally {
                refreshing.set(false)
            }
        }
    }

    private fun apply(context: Context, packages: String) {
        val uids = parsePackageList(packages).mapNotNullTo(HashSet()) { packageName ->
            resolveUid(context.packageManager, packageName)
        }
        if (uids != excludedUids) {
            Logger.info("DNS filtering skipped for ${uids.size} app UID(s)")
        }
        excludedUids = uids
        lastRefresh = SystemClock.elapsedRealtime()
    }

    @Suppress("DEPRECATION")
    private fun resolveUid(packageManager: PackageManager, packageName: String): Int? {
        return try {
            packageManager.getApplicationInfo(packageName, 0).uid
        } catch (e: PackageManager.NameNotFoundException) {
            // Uninstalled app: keep it in the settings, ignore it until it comes back.
            null
        }
    }
}
