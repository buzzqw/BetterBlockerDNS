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

package com.buzzqw.bbdns.presentation.screens.home.components

import com.buzzqw.bbdns.domain.model.Application

/** One-tap exclusions offered on top of the Apps tab. */
enum class AppFilterPreset {
    BANKING,
    SYSTEM,
    GOOGLE_SERVICES;

    /** Google services and system apps keep the VPN but skip the DNS filter. */
    val skipsDnsFilter: Boolean
        get() = this != BANKING
}

/**
 * Heuristics to recognise the apps that most often break when their traffic is
 * filtered: banking/payment apps, system components and apps that rely on
 * Google Play Services.
 */
object AppFilterPresets {

    /**
     * Package or name fragments used for banking and payment apps. Matching is
     * deliberately broad: users can always re-enable an app from its details.
     */
    private val BANKING_KEYWORDS = listOf(
        // Generic banking and payment words (any language).
        "bank", "banca", "banco", "bancoposta", "posteitaliane", "postepay",
        "posteid", "popolare", "cooperative", "rurale", "sparkasse", "volksbank",
        "raiffeisen", "fintech",

        // Italy.
        "unicredit", "intesa", "sanpaolo", "fineco", "widiba", "mediolanum",
        "fideuram", "bper", "bpdb", "credem", "carige", "illimity", "sella",
        "webank", "youbanking", "iccrea", "creditagricole", "credit-agricole",
        "cariparma", "carisbo", "popso", "bancobpm", "bancagenerali",
        "bancamediolanum", "bancasistema", "bancaprogetto", "bancaifis",
        "bancacf", "bancaetruria", "rivabanca", "emilbanca", "nexi",
        "satispay", "hype", "yap", "tinaba", "bancomat",
        "paypal", "venmo", "cashapp", "cash.app", "google.wallet", "wallet",
        "visa", "mastercard", "amex", "americanexpress", "klarna", "scalapay",
        "hello bank", "hellobank", "ubibanca", "youbanca",

        // Europe.
        "revolut", "n26", "monzo", "starling", "wise", "transferwise",
        "bunq", "vivid", "monese", "curve", "bbva", "santander",
        "deutschebank", "deutsche bank", "commerzbank", "dkb",
        "bnpparibas", "bnp paribas", "societegenerale", "societe generale",
        "creditmutuel", "credit mutuel", "labanquepostale", "boursorama",
        "hsbc", "barclays", "lloyds", "natwest", "halifax", "nationwide",
        "kbc", "belfius", "argenta", "abnamro", "abn amro",
        "rabobank", "triodos", "asnbank", "snsbank", "caixabank", "sabadell",
        "bankinter", "abanca", "unicaja", "cajamar", "kutxabank", "openbank",
        "evobanco", "millennium bcp", "millenniumbcp", "activobank",
        "novobanco", "montepio", "swedbank", "handelsbanken", "nordea",
        "danske", "opmobiili", "spankki", "mbank", "pekao", "alior",
        "monobank", "privat24", "otpbank", "erste", "bancatransilvania",
        "ingdirect", "ing.mobile", "ing bank", "ingbank",
        "pko bp", "qonto", "lydia", "fortuneo", "monabanq",
        "sumup", "zettle", "izettle", "paysera", "trustly", "zimpler",
        "mobilepay", "vipps", "tikkie", "payconiq", "bancontact",
        "itsme", "knab", "regiobank", "vanlanschot", "nibc", "crelan",
        "bawag", "comdirect", "traderepublic", "trade republic", "scalable",
        "degiro", "etoro", "bitpanda", "coinbase", "binance", "kraken",
        "crypto", "raisin", "weltsparen", "bnext", "cajasur", "ibercaja",
        "caixa", "moey", "eurobic", "aktia", "saastopankki",
        "lansforsakringar", "getin", "ferratum", "piraeus", "garanti",

        // Investments, trading and insurance often bundled with bank apps.
        "invest", "trading", "broker",
        "generali", "allianz", "unipol", "zurich", "quixa",
    )

    /**
     * Short or common brand tokens that would be too ambiguous as free-text
     * fragments but are safe when they appear as a whole package segment (for
     * example `it.mps.mobile`, `com.ing.mobile` or `com.chase.sig.android`).
     */
    private val BANKING_PACKAGE_TOKENS = listOf(
        "mps", "seb", "lcl", "tsb", "brd", "bcr", "pko", "otp", "ing", "bcc",
        "bnl", "dnb", "citi", "chase", "axa", "linear", "curve",
        "imaginecurve", "vivid", "nickel", "swish",
    )

    fun matches(app: Application, preset: AppFilterPreset): Boolean = when (preset) {
        AppFilterPreset.BANKING -> isBankingApp(app)
        AppFilterPreset.SYSTEM -> app.systemApp
        AppFilterPreset.GOOGLE_SERVICES -> app.usesGooglePlayServices
    }

    fun select(applications: List<Application>, preset: AppFilterPreset): List<Application> {
        val selected = applications.filter { matches(it, preset) }
        // Never propose an app that is already excluded.
        return if (preset.skipsDnsFilter) selected else selected.filterNot { it.bypassVpn }
    }

    private fun isBankingApp(app: Application): Boolean {
        val packageId = app.packageID.lowercase()
        val haystack = "$packageId ${app.displayName.lowercase()}"
        if (BANKING_KEYWORDS.any { haystack.contains(it) }) return true
        val tokens = packageId.split('.')
        return BANKING_PACKAGE_TOKENS.any { it in tokens }
    }
}
