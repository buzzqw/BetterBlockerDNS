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

/**
 * Well-known endpoints that let an app or a browser resolve names without
 * going through the VPN's DNS proxy.
 *
 * A DNS blocker only sees port 53. A browser that switches to DNS-over-HTTPS
 * resolves through port 443 and disappears from the filter, and Android's
 * Private DNS uses DNS-over-TLS on port 853. Blocking the hostnames below
 * stops the bootstrap of those clients; [KNOWN_DOH_IPS] covers the ones that
 * are configured by IP address and never ask DNS at all.
 *
 * This list is deliberately made of dedicated DNS endpoints only: no domain
 * here serves normal web content as well (the Cloudflare 1.1.1.1 page is the
 * only exception and it is reached through the IP block, which the toggle
 * accepts as a trade-off).
 */
object DohBypass {

    /** DNS-over-TLS port used by Android Private DNS and stub resolvers. */
    const val SECURE_DNS_PORT = 853

    /** HTTPS port for DNS-over-HTTPS and for hardcoded DoH IPs. */
    const val HTTPS_PORT = 443

    /**
     * Firefox and other resolvers disable their DoH when this name is
     * answered with NXDOMAIN: it is the official canary domain.
     */
    const val CANARY_DOMAIN = "use-application-dns.net"

    /** Hostnames (and their subdomains) of dedicated DoH endpoints. */
    val domains: Set<String> = setOf(
        CANARY_DOMAIN,
        "dns.google",
        "cloudflare-dns.com",
        "dns.quad9.net",
        "dns.adguard-dns.com",
        "dns.adguard.com",
        "doh.opendns.com",
        "doh.cleanbrowsing.org",
        "dns.nextdns.io",
        "doh.dns.sb",
        "dns.mullvad.net",
        "dns.controld.com",
        "dns.brave.com",
        "doh.opera.com",
    )

    /**
     * Public resolvers whose DoH service answers on 443 without any DNS
     * lookup, so blocking the hostname is not enough. These addresses are
     * dedicated to DNS; no ordinary website is hosted on 443 there.
     */
    val KNOWN_DOH_IPS: Set<String> = setOf(
        "1.1.1.1",       // Cloudflare
        "1.0.0.1",       // Cloudflare
        "1.1.1.2",       // Cloudflare security
        "1.0.0.2",       // Cloudflare security
        "1.1.1.3",       // Cloudflare family
        "1.0.0.3",       // Cloudflare family
        "8.8.8.8",       // Google
        "8.8.4.4",       // Google
        "9.9.9.9",       // Quad9
        "149.112.112.112", // Quad9
        "94.140.14.14",  // AdGuard
        "94.140.15.15",  // AdGuard
        "208.67.222.222", // OpenDNS
        "208.67.220.220", // OpenDNS
        "185.228.168.168", // CleanBrowsing
        "185.228.169.168", // CleanBrowsing
        "76.76.2.0",     // Control D
        "76.76.10.0",    // Control D
    )

    /**
     * True when [host] is a known DoH endpoint. Matching is on label
     * boundaries, so `notdns.google.evil.com` or `dns.google.example` do not
     * match `dns.google`.
     */
    fun matches(host: String): Boolean {
        val normalized = host.trim().trimEnd('.').lowercase()
        if (normalized.isEmpty()) return false
        return domains.any { normalized == it || normalized.endsWith(".$it") }
    }

    /** True when [ip] is a public resolver that also speaks DoH on 443. */
    fun isKnownDohIp(ip: String): Boolean = ip.trim() in KNOWN_DOH_IPS

    /**
     * True when a first packet to [destinationIp]:[destinationPort] must be
     * dropped because it would open an encrypted DNS channel.
     *
     * [protocol] is the IP protocol number (6 = TCP, 17 = UDP).
     */
    fun blocksTransport(protocol: Int, destinationPort: Int, destinationIp: String): Boolean {
        val stream = protocol == IPPROTO_TCP || protocol == IPPROTO_UDP
        if (!stream) return false
        if (destinationPort == SECURE_DNS_PORT) return true
        return destinationPort == HTTPS_PORT && isKnownDohIp(destinationIp)
    }

    private const val IPPROTO_TCP = 6
    private const val IPPROTO_UDP = 17
}
