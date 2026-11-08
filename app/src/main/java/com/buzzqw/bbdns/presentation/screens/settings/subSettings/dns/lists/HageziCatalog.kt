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

/**
 * Native catalog of the HaGeZi DNS blocklists.
 *
 * BBDNS uses the `wildcard/<list>-onlydomains.txt` builds because they contain plain
 * domain names (one per line), which is exactly the format understood by the
 * built-in [com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.BlocklistParser].
 * The lists are fetched and refreshed by the normal blocklist update worker, so
 * they participate in auto-update, caching and the domain database like any
 * other list.
 */
enum class HageziCategory(@StringRes val titleRes: Int, @StringRes val descriptionRes: Int) {
    CORE(
        R.string.catalog_hagezi_category_core_title,
        R.string.catalog_hagezi_category_core_desc
    ),
    SECURITY(
        R.string.catalog_hagezi_category_security_title,
        R.string.catalog_hagezi_category_security_desc
    ),
    PRIVACY(
        R.string.catalog_hagezi_category_privacy_title,
        R.string.catalog_hagezi_category_privacy_desc
    ),
    FAMILY(
        R.string.catalog_hagezi_category_family_title,
        R.string.catalog_hagezi_category_family_desc
    ),
    NETWORK(
        R.string.catalog_hagezi_category_network_title,
        R.string.catalog_hagezi_category_network_desc
    )
}

data class HageziList(
    override val id: String,
    override val name: String,
    override val description: String,
    @StringRes override val descriptionRes: Int? = null,
    val file: String,
    val category: HageziCategory,
    override val recommended: Boolean = false,
    /** True for the mutually-exclusive Multi tiers (Light/Normal/Pro/Pro++/Ultimate). */
    val coreTier: Boolean = false,
    /** True for the Threat Intelligence Feed variants. */
    val threatFeed: Boolean = false,
    /** Alternatives group for the other variant families (gambling, DoH). */
    val group: String? = null
) : BlocklistSource {
    override val url: String get() = "$BASE_URL/$file"

    // HaGeZi: "pick exactly one" Multi tier, and size-optimised versions are
    // "alternatives to their full list, never something you add on top".
    override val exclusiveGroup: String?
        get() = when {
            coreTier -> "hagezi.multi"
            threatFeed -> "hagezi.tif"
            else -> group
        }

    companion object {
        /** Single source of truth for HaGeZi download URLs. */
        const val BASE_URL = "https://cdn.jsdelivr.net/gh/hagezi/dns-blocklists@latest/wildcard"
    }
}

data class HageziProfile(
    val id: String,
    val name: String,
    @StringRes val nameRes: Int,
    val description: String,
    @StringRes val descriptionRes: Int,
    val listIds: List<String>,
    val recommended: Boolean = false
)

object HageziCatalog {

    // ---------------------------------------------------------------------
    // Core Multi tiers (choose one)
    // ---------------------------------------------------------------------

    val LIGHT = HageziList(
        id = "light",
        name = "HaGeZi Multi LIGHT",
        description = "Basic protection. Blocks ads, trackers and telemetry with minimal breakage. Only domains from the Top 1M/10M lists.",
        descriptionRes = R.string.catalog_hagezi_light_desc,
        file = "light-onlydomains.txt",
        category = HageziCategory.CORE,
        coreTier = true
    )

    val NORMAL = HageziList(
        id = "multi",
        name = "HaGeZi Multi NORMAL",
        description = "All-round protection. Ads, trackers, metrics, phishing, malware, scams and cryptojacking.",
        descriptionRes = R.string.catalog_hagezi_normal_desc,
        file = "multi-onlydomains.txt",
        category = HageziCategory.CORE,
        coreTier = true
    )

    val PRO = HageziList(
        id = "pro",
        name = "HaGeZi Multi PRO",
        description = "Extended protection (recommended). Also blocks crash reporters and more telemetry. Low to moderate risk of breakage.",
        descriptionRes = R.string.catalog_hagezi_pro_desc,
        file = "pro-onlydomains.txt",
        category = HageziCategory.CORE,
        recommended = true,
        coreTier = true
    )

    val PRO_MINI = HageziList(
        id = "pro.mini",
        name = "HaGeZi Multi PRO mini",
        description = "Size-optimised PRO for devices with limited RAM. Same idea, roughly half the entries.",
        descriptionRes = R.string.catalog_hagezi_pro_mini_desc,
        file = "pro.mini-onlydomains.txt",
        category = HageziCategory.CORE,
        coreTier = true
    )

    val PRO_PLUS = HageziList(
        id = "pro.plus",
        name = "HaGeZi Multi PRO++",
        description = "Advanced protection. More aggressive than PRO, with a moderate risk of blocking legitimate domains.",
        descriptionRes = R.string.catalog_hagezi_pro_plus_desc,
        file = "pro.plus-onlydomains.txt",
        category = HageziCategory.CORE,
        coreTier = true
    )

    val PRO_PLUS_MINI = HageziList(
        id = "pro.plus.mini",
        name = "HaGeZi Multi PRO++ mini",
        description = "Size-optimised PRO++ for low-memory devices.",
        descriptionRes = R.string.catalog_hagezi_pro_plus_mini_desc,
        file = "pro.plus.mini-onlydomains.txt",
        category = HageziCategory.CORE,
        coreTier = true
    )

    val ULTIMATE = HageziList(
        id = "ultimate",
        name = "HaGeZi Multi ULTIMATE",
        description = "Maximum protection. Very aggressive and can break apps or sites. Recommended only for experienced users.",
        descriptionRes = R.string.catalog_hagezi_ultimate_desc,
        file = "ultimate-onlydomains.txt",
        category = HageziCategory.CORE,
        coreTier = true
    )

    val ULTIMATE_MINI = HageziList(
        id = "ultimate.mini",
        name = "HaGeZi Multi ULTIMATE mini",
        description = "Size-optimised ULTIMATE for low-memory devices.",
        descriptionRes = R.string.catalog_hagezi_ultimate_mini_desc,
        file = "ultimate.mini-onlydomains.txt",
        category = HageziCategory.CORE,
        coreTier = true
    )

    // ---------------------------------------------------------------------
    // Threat Intelligence Feeds
    // ---------------------------------------------------------------------

    val TIF = HageziList(
        id = "tif",
        name = "HaGeZi TIF (full)",
        description = "Threat Intelligence Feeds. A serious security boost against live malware, phishing and scam infrastructure. Works alongside any Multi tier.",
        descriptionRes = R.string.catalog_hagezi_tif_desc,
        file = "tif-onlydomains.txt",
        category = HageziCategory.SECURITY,
        recommended = true,
        threatFeed = true
    )

    val TIF_MEDIUM = HageziList(
        id = "tif.medium",
        name = "HaGeZi TIF medium",
        description = "Balanced Threat Intelligence Feeds: strong security coverage with fewer entries.",
        descriptionRes = R.string.catalog_hagezi_tif_medium_desc,
        file = "tif.medium-onlydomains.txt",
        category = HageziCategory.SECURITY,
        threatFeed = true
    )

    val TIF_MINI = HageziList(
        id = "tif.mini",
        name = "HaGeZi TIF mini",
        description = "Compact Threat Intelligence Feeds for low-memory devices or slow connections.",
        descriptionRes = R.string.catalog_hagezi_tif_mini_desc,
        file = "tif.mini-onlydomains.txt",
        category = HageziCategory.SECURITY,
        threatFeed = true
    )

    // ---------------------------------------------------------------------
    // Other security / anti-abuse lists
    // ---------------------------------------------------------------------

    val FAKE = HageZi(
        id = "fake",
        name = "HaGeZi Fake",
        description = "Blocks fake shops, scam sites, traps and fraudulent domains.",
        descriptionRes = R.string.catalog_hagezi_fake_desc,
        file = "fake-onlydomains.txt",
        category = HageziCategory.SECURITY
    )

    val POPUP_ADS = HageZi(
        id = "popupads",
        name = "HaGeZi Pop-Up Ads",
        description = "Blocks annoying and malicious pop-up ad infrastructure.",
        descriptionRes = R.string.catalog_hagezi_popup_ads_desc,
        file = "popupads-onlydomains.txt",
        category = HageziCategory.SECURITY
    )

    val DYNDNS = HageZi(
        id = "dyndns",
        name = "HaGeZi DynDNS",
        description = "Guards against dynamic DNS abuse often used by malware command-and-control.",
        descriptionRes = R.string.catalog_hagezi_dyndns_desc,
        file = "dyndns-onlydomains.txt",
        category = HageziCategory.NETWORK
    )

    val HOSTER = HageZi(
        id = "hoster",
        name = "HaGeZi Badware Hoster",
        description = "Blocks hosting providers with a strong concentration of malicious content.",
        descriptionRes = R.string.catalog_hagezi_hoster_desc,
        file = "hoster-onlydomains.txt",
        category = HageziCategory.SECURITY
    )

    val SPAM_TLDS = HageZi(
        id = "spam-tlds",
        name = "HaGeZi Most Abused TLDs",
        description = "Blocks top-level domains that are disproportionately used for spam and scams.",
        descriptionRes = R.string.catalog_hagezi_spam_tlds_desc,
        file = "spam-tlds-onlydomains.txt",
        category = HageziCategory.NETWORK
    )

    val DOH_VPN_PROXY_BYPASS = HageZi(
        id = "doh-vpn-proxy-bypass",
        name = "HaGeZi DoH/VPN/Tor/Proxy Bypass",
        description = "Stops apps and browsers from bypassing your DNS filtering through DoH, VPNs, Tor or proxies.",
        descriptionRes = R.string.catalog_hagezi_doh_vpn_proxy_bypass_desc,
        file = "doh-vpn-proxy-bypass-onlydomains.txt",
        category = HageziCategory.NETWORK,
        group = "hagezi.doh"
    )

    val DOH = HageZi(
        id = "doh",
        name = "HaGeZi DoH only",
        description = "Only the DNS-over-HTTPS bypass entries, without the VPN/Tor/proxy part. Alternative to the full bypass list.",
        descriptionRes = R.string.catalog_hagezi_doh_desc,
        file = "doh-onlydomains.txt",
        category = HageziCategory.NETWORK,
        group = "hagezi.doh"
    )

    val URL_SHORTENER = HageZi(
        id = "urlshortener",
        name = "HaGeZi URL Shortener",
        description = "Blocks link shorteners often used to hide malicious destinations.",
        descriptionRes = R.string.catalog_hagezi_url_shortener_desc,
        file = "urlshortener-onlydomains.txt",
        category = HageziCategory.FAMILY
    )

    val ANTI_PIRACY = HageZi(
        id = "anti.piracy",
        name = "HaGeZi Anti-Piracy",
        description = "Blocks piracy portals and their infrastructure. May also affect some streaming links.",
        descriptionRes = R.string.catalog_hagezi_anti_piracy_desc,
        file = "anti.piracy-onlydomains.txt",
        category = HageziCategory.FAMILY
    )

    // ---------------------------------------------------------------------
    // Privacy / native trackers
    // ---------------------------------------------------------------------

    val NATIVE_APPLE = HageZi(
        id = "native.apple",
        name = "Native Trackers — Apple",
        description = "Blocks Apple device telemetry and tracking endpoints.",
        descriptionRes = R.string.catalog_hagezi_native_apple_desc,
        file = "native.apple-onlydomains.txt",
        category = HageziCategory.PRIVACY
    )

    val NATIVE_SAMSUNG = HageZi(
        id = "native.samsung",
        name = "Native Trackers — Samsung",
        description = "Blocks Samsung device telemetry and tracking endpoints.",
        descriptionRes = R.string.catalog_hagezi_native_samsung_desc,
        file = "native.samsung-onlydomains.txt",
        category = HageziCategory.PRIVACY
    )

    val NATIVE_XIAOMI = HageZi(
        id = "native.xiaomi",
        name = "Native Trackers — Xiaomi",
        description = "Blocks Xiaomi/MIUI telemetry and tracking endpoints.",
        descriptionRes = R.string.catalog_hagezi_native_xiaomi_desc,
        file = "native.xiaomi-onlydomains.txt",
        category = HageziCategory.PRIVACY
    )

    val NATIVE_TIKTOK = HageZi(
        id = "native.tiktok",
        name = "Native Trackers — TikTok",
        description = "Blocks TikTok's embedded tracking and telemetry endpoints.",
        descriptionRes = R.string.catalog_hagezi_native_tiktok_desc,
        file = "native.tiktok-onlydomains.txt",
        category = HageziCategory.PRIVACY
    )

    val NO_SAFESEARCH = HageZi(
        id = "nosafesearch",
        name = "HaGeZi No Safesearch",
        description = "Blocks search engines and apps that do not respect SafeSearch.",
        descriptionRes = R.string.catalog_hagezi_no_safesearch_desc,
        file = "nosafesearch-onlydomains.txt",
        category = HageziCategory.FAMILY
    )

    // ---------------------------------------------------------------------
    // Family / content
    // ---------------------------------------------------------------------

    val NSFW = HageZi(
        id = "nsfw",
        name = "HaGeZi NSFW",
        description = "Blocks adult content.",
        descriptionRes = R.string.catalog_hagezi_nsfw_desc,
        file = "nsfw-onlydomains.txt",
        category = HageziCategory.FAMILY
    )

    val GAMBLING = HageZi(
        id = "gambling",
        name = "HaGeZi Gambling",
        description = "Blocks gambling and betting sites.",
        descriptionRes = R.string.catalog_hagezi_gambling_desc,
        file = "gambling-onlydomains.txt",
        category = HageziCategory.FAMILY,
        group = "hagezi.gambling"
    )

    val GAMBLING_MEDIUM = HageZi(
        id = "gambling.medium",
        name = "HaGeZi Gambling medium",
        description = "Medium-strength gambling filter.",
        descriptionRes = R.string.catalog_hagezi_gambling_medium_desc,
        file = "gambling.medium-onlydomains.txt",
        category = HageziCategory.FAMILY,
        group = "hagezi.gambling"
    )

    val GAMBLING_MINI = HageZi(
        id = "gambling.mini",
        name = "HaGeZi Gambling mini",
        description = "Compact gambling filter.",
        descriptionRes = R.string.catalog_hagezi_gambling_mini_desc,
        file = "gambling.mini-onlydomains.txt",
        category = HageziCategory.FAMILY,
        group = "hagezi.gambling"
    )

    val SOCIAL = HageZi(
        id = "social",
        name = "HaGeZi Social Networks",
        description = "Blocks access to social networks.",
        descriptionRes = R.string.catalog_hagezi_social_desc,
        file = "social-onlydomains.txt",
        category = HageziCategory.FAMILY
    )

    /** All catalog entries, in display order. */
    val all: List<HageziList> = listOf(
        LIGHT, NORMAL, PRO, PRO_MINI, PRO_PLUS, PRO_PLUS_MINI, ULTIMATE, ULTIMATE_MINI,
        TIF, TIF_MEDIUM, TIF_MINI,
        FAKE, POPUP_ADS, HOSTER, DYNDNS, SPAM_TLDS, DOH_VPN_PROXY_BYPASS, DOH,
        URL_SHORTENER, ANTI_PIRACY,
        NATIVE_APPLE, NATIVE_SAMSUNG, NATIVE_XIAOMI, NATIVE_TIKTOK,
        NO_SAFESEARCH, NSFW, GAMBLING, GAMBLING_MEDIUM, GAMBLING_MINI, SOCIAL
    )

    val byId: Map<String, HageziList> = all.associateBy { it.id }

    /** IDs managed by a protection profile: every Multi tier and every TIF variant. */
    val profileManagedIds: Set<String> =
        all.filter { it.coreTier || it.threatFeed }.map { it.id }.toSet()

    val profiles: List<HageziProfile> = listOf(
        HageziProfile(
            id = "essential",
            name = "Essential",
            nameRes = R.string.catalog_profile_essential_name,
            description = "Light browsing protection with a compact threat feed. Very low breakage.",
            descriptionRes = R.string.catalog_profile_essential_desc,
            listIds = listOf(LIGHT.id, TIF_MINI.id)
        ),
        HageziProfile(
            id = "balanced",
            name = "Balanced",
            nameRes = R.string.catalog_profile_balanced_name,
            description = "All-round ad, tracker and malware blocking with a compact TIF.",
            descriptionRes = R.string.catalog_profile_balanced_desc,
            listIds = listOf(NORMAL.id, TIF_MINI.id),
            recommended = true
        ),
        HageziProfile(
            id = "pro",
            name = "Pro",
            nameRes = R.string.catalog_profile_pro_name,
            description = "HaGeZi's recommended setup: Multi PRO plus the full Threat Intelligence Feed (TIF).",
            descriptionRes = R.string.catalog_profile_pro_desc,
            listIds = listOf(PRO.id, TIF.id)
        ),
        HageziProfile(
            id = "aggressive",
            name = "Aggressive",
            nameRes = R.string.catalog_profile_aggressive_name,
            description = "Multi PRO++ plus the full TIF. Strong protection, some sites may need a manual allow rule.",
            descriptionRes = R.string.catalog_profile_aggressive_desc,
            listIds = listOf(PRO_PLUS.id, TIF.id)
        ),
        HageziProfile(
            id = "maximum",
            name = "Maximum",
            nameRes = R.string.catalog_profile_maximum_name,
            description = "Ultimate plus the full TIF. Only for users who want the strictest filtering.",
            descriptionRes = R.string.catalog_profile_maximum_desc,
            listIds = listOf(ULTIMATE.id, TIF.id)
        )
    )

    /**
     * Returns the profile that exactly matches the currently enabled profile-managed
     * lists, or null when the selection is custom/mixed.
     */
    fun matchingProfile(enabledUrlsOrIds: Set<String>): HageziProfile? {
        val enabledIds = enabledUrlsOrIds.mapNotNull { value ->
            all.firstOrNull { it.url == value || it.id == value }?.id
        }.filter { it in profileManagedIds }.toSet()
        if (enabledIds.isEmpty()) return null
        profiles.firstOrNull { it.listIds.toSet() == enabledIds }?.let { return it }
        // Also mark the profile as active when extra lists are enabled on top of it.
        return profiles
            .filter { profile -> profile.listIds.isNotEmpty() && profile.listIds.all { it in enabledIds } }
            .maxByOrNull { it.listIds.size }
    }
}

/** Small alias to keep the catalog declarations readable. */
private typealias HageZi = HageziList
