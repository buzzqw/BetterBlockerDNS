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
        "bank", "banca", "banco", "bancoposta", "posteitaliane", "postepay",
        "unicredit", "intesa", "sanpaolo", "fineco", "widiba", "mediolanum",
        "fideuram", "bper", "bpdb", "credem", "carige", "popolare", "cooperative",
        "revolut", "n26", "monzo", "starling", "wise", "transferwise",
        "satispay", "hype", "yap", "tinaba", "bancomat", "nexi",
        "paypal", "venmo", "cashapp", "cash.app", "google.wallet", "wallet",
        "visa", "mastercard", "amex", "americanexpress", "klarna", "scalapay",
        "ubibanca", "youbanca", "hello bank", "hellobank",
        "invest", "trading", "broker",
        "generali", "allianz", "unipol", "zurich", "axa", "linear", "quixa",
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
        val haystack = (app.packageID + " " + app.displayName).lowercase()
        return BANKING_KEYWORDS.any { haystack.contains(it) }
    }
}
