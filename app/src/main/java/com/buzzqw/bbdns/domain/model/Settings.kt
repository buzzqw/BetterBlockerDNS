/*
 * Copyright (C) 2025-2026 Vexzure
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

package com.buzzqw.bbdns.domain.model

import com.buzzqw.bbdns.presentation.navigation.routes.HomeRoutes
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants

data class Settings(
    // Hidden
    val isFirstTimeRunning: Boolean = true,

    //Privacy
    var screenProtection: Boolean = false,
    val blockPort80: Boolean = false,

    //Lock
    var defaultRoute: String = HomeRoutes.Home.route,
    var passcode: String? = null,
    var fingerprint: Boolean = false,
    var pattern: String? = null,
    val lockImmediately: Boolean = true,

    //Behaviour
    val logs: Boolean = true,
    /** When true the log screen shows full hostnames, ports and packet endpoints. */
    val detailedLogs: Boolean = false,
    val showSystemPackages: Boolean = false,
    val showOfflinePackages: Boolean = false,
    val wiFiDefault: Boolean = true,
    val cellularDefault: Boolean = true,
    val startOnBoot: Boolean = true,

    //Network
    val ipv4: String = NetworkConstants.VPN_ADDRESS,
    val ipv6: String = NetworkConstants.VPN6_ADDRESS,
    val allowLocal: Boolean = true,
    val blockWifiWhenScreenOff: Boolean = false,
    val blockCellularWhenScreenOff: Boolean = false,
    val permanentNotification: Boolean = false,
    val dnsServer1: String = NetworkConstants.DNS_SERVERS[0].ipv4Primary,
    val dnsServer2: String = NetworkConstants.DNS_SERVERS[0].ipv4Secondary,
    val dnsServer1v6: String = NetworkConstants.DNS_SERVERS[0].ipv6Primary,
    val dnsServer2v6: String = NetworkConstants.DNS_SERVERS[0].ipv6Secondary,
    // DNS over HTTPS (RFC 8484). The plain DNS servers above remain the fallback.
    val dohEnabled: Boolean = false,
    val dohUrl: String = "https://cloudflare-dns.com/dns-query",
    val networkSpeedMonitor: Boolean = false,

    // Themes
    val automaticTheme: Boolean = true,
    val darkTheme: Boolean = false,
    val useDynamicIcons: Boolean = false,
    var dynamicTheme: Boolean = false,
    var amoledTheme: Boolean = false,
    var customColor: Int = -14975141, // BBDNS brand green (0xFF1B7F5B)
    var showDialog: Boolean = true,

    // Notifications
    val sendNotificationOnInstall: Boolean = false,

    // Nflog
    val PID: Int = 0,

    // DNS Blocking
    val malwareProtection: Boolean = false,
    val adBlocker: Boolean = false,
    val trackerProtection: Boolean = false,
    val autoUpdateEnabled: Boolean = true,
    val autoUpdateInterval: Long = 24 * 60 * 60 * 1000L,
    // Every feature is available without purchases.
    val premiumUnlocked: Boolean = true,
    /** True while the user wants protection running (used by the watchdog). */
    val protectionEnabled: Boolean = false,
    /** One-time seeding of the recommended blocklist profile. */
    val defaultBlocklistsSeeded: Boolean = false,
    /** The home "stability" tip (Always-on VPN, battery) was dismissed. */
    val stabilityTipDismissed: Boolean = false,
    /**
     * Comma-separated package names whose DNS queries skip the blocklists.
     * These apps stay inside the VPN; only DNS filtering is skipped.
     */
    val dnsFilterExcludedPackages: String = "",

    val dontShowHelp: Boolean = false
)

// Helpers are top-level functions on purpose: Settings is persisted by
// reflection over its declared fields, so it must not get a companion object
// or extra backing fields.

/** Parses a comma-separated package list, ignoring blanks and duplicates. */
fun parsePackageList(value: String): Set<String> =
    value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

/** Serialises a package set into the comma-separated storage format. */
fun serializePackageList(packages: Collection<String>): String =
    packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted().joinToString(",")

fun Settings.dnsFilterExcludedPackageSet(): Set<String> = parsePackageList(dnsFilterExcludedPackages)

fun Settings.isDnsFilterExcluded(packageName: String): Boolean =
    packageName in dnsFilterExcludedPackageSet()

fun Settings.withDnsFilterExcluded(packageName: String, excluded: Boolean): Settings {
    val packages = dnsFilterExcludedPackageSet().toMutableSet()
    if (excluded) packages.add(packageName) else packages.remove(packageName)
    return copy(dnsFilterExcludedPackages = serializePackageList(packages))
}
