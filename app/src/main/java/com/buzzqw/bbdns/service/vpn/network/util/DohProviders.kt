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

package com.buzzqw.bbdns.service.vpn.network.util

import androidx.annotation.StringRes
import com.buzzqw.bbdns.R

data class DohProvider(
    val name: String,
    val url: String,
    @StringRes val descriptionRes: Int? = null,
    val recommended: Boolean = false
)

/**
 * Public DNS-over-HTTPS endpoints verified with RFC 8484 POST requests.
 * All of them accept the raw DNS wire format as `application/dns-message`.
 */
object DohProviders {

    val all: List<DohProvider> = listOf(
        DohProvider("Cloudflare", "https://cloudflare-dns.com/dns-query", R.string.doh_desc_cloudflare, recommended = true),
        DohProvider("Cloudflare Security", "https://security.cloudflare-dns.com/dns-query", R.string.doh_desc_cloudflare_security),
        DohProvider("Cloudflare Family", "https://family.cloudflare-dns.com/dns-query", R.string.doh_desc_cloudflare_family),
        DohProvider("Google", "https://dns.google/dns-query", R.string.doh_desc_google),
        DohProvider("Quad9", "https://dns.quad9.net/dns-query", R.string.doh_desc_quad9),
        DohProvider("Quad9 Unsecured", "https://dns10.quad9.net/dns-query", R.string.doh_desc_quad9_unsecured),
        DohProvider("Quad9 ECS", "https://dns11.quad9.net/dns-query", R.string.doh_desc_quad9_ecs),
        DohProvider("AdGuard", "https://dns.adguard-dns.com/dns-query", R.string.doh_desc_adguard),
        DohProvider("AdGuard Family", "https://family.adguard-dns.com/dns-query", R.string.doh_desc_adguard_family),
        DohProvider("DNS.SB", "https://doh.sb/dns-query", R.string.doh_desc_dns_sb),
        DohProvider("Control D", "https://freedns.controld.com/p0", R.string.doh_desc_control_d),
        DohProvider("Control D Malware", "https://freedns.controld.com/p1", R.string.doh_desc_control_d_malware),
        DohProvider("Control D Ads", "https://freedns.controld.com/p2", R.string.doh_desc_control_d_ads),
        DohProvider("Control D Family", "https://freedns.controld.com/p4", R.string.doh_desc_control_d_family),
        DohProvider("DNSBunker", "https://dnsbunker.org/dns-query", R.string.doh_desc_dnsbunker),
        DohProvider("DNS4EU Unfiltered", "https://unfiltered.joindns4.eu/dns-query", R.string.doh_desc_dns4eu_unfiltered),
        DohProvider("DNS4EU Protective", "https://protective.joindns4.eu/dns-query", R.string.doh_desc_dns4eu_protective),
        DohProvider("DNS4EU Child", "https://child.joindns4.eu/dns-query", R.string.doh_desc_dns4eu_child),
        DohProvider("DNS4EU NoAds", "https://noads.joindns4.eu/dns-query", R.string.doh_desc_dns4eu_noads),
        DohProvider("DNS4EU Child + NoAds", "https://child-noads.joindns4.eu/dns-query", R.string.doh_desc_dns4eu_child_noads)
    )

    /** Provider name for a known URL, or null for a custom endpoint. */
    fun nameOrNull(url: String): String? = all.firstOrNull { it.url == url }?.name

    fun isCustom(url: String): Boolean = all.none { it.url == url }
}
