/*
 * Copyright (C) 2025-2026 Vexzure
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

package com.buzzqw.bbdns.service.vpn.network.util

object NetworkConstants {
    // DNS server structure: Name -> (IPv4 Primary, IPv4 Secondary, IPv6 Primary, IPv6 Secondary)
    data class DnsServer(
        val name: String,
        val ipv4Primary: String,
        val ipv4Secondary: String,
        val ipv6Primary: String,
        val ipv6Secondary: String
    )

    /**
     * Free public resolvers. Family/security variants are listed explicitly so
     * the user can pick the behaviour they want instead of relying on a single
     * filtered endpoint.
     */
    val DNS_SERVERS: List<DnsServer> = listOf(
        // Google
        DnsServer("Google Public DNS", "8.8.8.8", "8.8.4.4", "2001:4860:4860::8888", "2001:4860:4860::8844"),

        // Cloudflare
        DnsServer("Cloudflare", "1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
        DnsServer("Cloudflare Malware", "1.1.1.2", "1.0.0.2", "2606:4700:4700::1112", "2606:4700:4700::1002"),
        DnsServer("Cloudflare Family", "1.1.1.3", "1.0.0.3", "2606:4700:4700::1113", "2606:4700:4700::1003"),

        // Quad9
        DnsServer("Quad9", "9.9.9.9", "149.112.112.112", "2620:fe::fe", "2620:fe::9"),
        DnsServer("Quad9 Unsecured", "9.9.9.10", "149.112.112.10", "2620:fe::10", "2620:fe::fe:10"),
        DnsServer("Quad9 ECS", "9.9.9.11", "149.112.112.11", "2620:fe::11", "2620:fe::fe:11"),

        // AdGuard DNS
        DnsServer("AdGuard DNS", "94.140.14.14", "94.140.15.15", "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff"),
        DnsServer("AdGuard Family", "94.140.14.15", "94.140.15.16", "2a10:50c0::bad1:ff", "2a10:50c0::bad2:ff"),
        DnsServer("AdGuard Non-filtering", "94.140.14.140", "94.140.14.141", "2a10:50c0::1:ff", "2a10:50c0::2:ff"),

        // OpenDNS / Cisco
        DnsServer("OpenDNS Home", "208.67.222.222", "208.67.220.220", "2620:119:35::35", "2620:119:53::53"),
        DnsServer("OpenDNS FamilyShield", "208.67.222.123", "208.67.220.123", "2620:119:35::123", "2620:119:53::123"),

        // CleanBrowsing
        DnsServer("CleanBrowsing Family", "185.228.168.168", "185.228.169.168", "2a0d:2a00:1::1", "2a0d:2a00:2::1"),
        DnsServer("CleanBrowsing Adult", "185.228.168.10", "185.228.169.11", "2a0d:2a00:1::2", "2a0d:2a00:2::2"),
        DnsServer("CleanBrowsing Security", "185.228.168.9", "185.228.169.9", "2a0d:2a00:1::", "2a0d:2a00:2::"),

        // Privacy-first European / alternative resolvers
        DnsServer("DNS.SB", "185.222.222.222", "45.11.45.11", "2a09::", "2a11::"),
        DnsServer("Control D", "76.76.2.0", "76.76.10.0", "2606:1a40::", "2606:1a40:1::"),
        DnsServer("Control D Malware", "76.76.2.1", "76.76.10.1", "2606:1a40::1", "2606:1a40:1::1"),
        DnsServer("Control D Ads", "76.76.2.2", "76.76.10.2", "2606:1a40::2", "2606:1a40:1::2"),
        DnsServer("Control D Family", "76.76.2.4", "76.76.10.4", "2606:1a40::4", "2606:1a40:1::4"),

        // DNS4EU
        DnsServer("DNS4EU Unfiltered", "86.54.11.100", "86.54.11.200", "2a13:1001::86:54:11:100", "2a13:1001::86:54:11:200"),
        DnsServer("DNS4EU Protective", "86.54.11.1", "86.54.11.201", "2a13:1001::86:54:11:1", "2a13:1001::86:54:11:201"),
        DnsServer("DNS4EU Child", "86.54.11.12", "86.54.11.212", "2a13:1001::86:54:11:12", "2a13:1001::86:54:11:212"),
        DnsServer("DNS4EU AdBlock", "86.54.11.13", "86.54.11.213", "2a13:1001::86:54:11:13", "2a13:1001::86:54:11:213"),

        // Other well-known free resolvers
        // Yandex
        DnsServer("Yandex Basic", "77.88.8.8", "77.88.8.1", "2a02:6b8::feed:0ff", "2a02:6b8:0:1::feed:0ff"),
        DnsServer("Yandex Safe", "77.88.8.88", "77.88.8.2", "2a02:6b8::feed:bad", "2a02:6b8:0:1::feed:bad"),
        DnsServer("Yandex Family", "77.88.8.7", "77.88.8.3", "2a02:6b8::feed:a11", "2a02:6b8:0:1::feed:a11")
    )

    // Legacy compatibility - convert to old format
    @Deprecated("Use DNS_SERVERS directly")
    val DNS_SERVERS_LEGACY: List<Pair<String, Pair<String, String>>> = DNS_SERVERS.map {
        Pair(it.name, Pair(it.ipv4Primary, it.ipv4Secondary))
    }
    const val VPN_ADDRESS: String = "10.0.10.1"
    const val VPN_DNS_ADDRESS: String = "198.18.0.1"  // Local DNS for IPv4
    /** Second local DNS address: Android uses it when the primary times out. */
    const val VPN_DNS_ADDRESS_ALT: String = "198.18.0.2"
    const val VPN_DNS6_ADDRESS: String = "fd00::53"    // Local DNS for IPv6
    const val ALL_TRAFFIC: String = "0.0.0.0"
    const val VPN6_ADDRESS: String = "fd00:1:fd00:1:fd00:1:fd00:1"

    const val ACTION_START_VPN = "com.buzzqw.bbdns.START_VPN"
    const val ACTION_STOP_VPN = "com.buzzqw.bbdns.vpn.controller.ACTION_STOP_VPN"

    const val ACTION_TOGGLE_WIFI = "com.buzzqw.bbdns.vpn.controller.ACTION_TOGGLE_WIFI"
    const val ACTION_TOGGLE_CELLURAL = "com.buzzqw.bbdns.vpn.controller.ACTION_TOGGLE_CELLULAR"

    const val ACTION_CLEAR_SESSIONS =  "com.buzzqw.bbdns.vpn.controller.ACTION_CLEAR_SESSIONS"

    const val TCP_PROTOCOL: Byte = 6
    const val UDP_PROTOCOL: Byte = 17
    const val ICMP_PROTOCOL: Byte = 1
    const val MAX_PACKET_LEN = 8 * 1024
}
