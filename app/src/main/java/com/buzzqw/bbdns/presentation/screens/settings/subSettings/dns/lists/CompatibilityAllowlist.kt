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

import android.content.Context
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.Configuration
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostException
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState

/**
 * Domain suffixes that BBDNS keeps reachable when aggressive HaGeZi tiers are
 * enabled. These cover the infrastructure that usually breaks when a DNS
 * blocklist is too eager: banking and payments, government/identity, app stores,
 * push notifications, captive-portal checks and login flows.
 *
 * The rule database performs suffix matching, so allowing `paypal.com` also
 * allows `www.paypal.com` and `api.paypal.com`.
 */
object CompatibilityAllowlist {

    const val EXCEPTION_TITLE = "BBDNS Compatibility"

    private const val PREFS = "bbdns_compatibility"
    private const val KEY_DEFAULT_ENABLED = "default_enabled"

    /**
     * Enables the compatibility allowlist once, on the first launch after this
     * version. A preference flag makes sure a later manual disable is not
     * overridden again on the next start.
     */
    fun ensureEnabledByDefault(context: Context, configuration: Configuration) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DEFAULT_ENABLED, false)) return
        try {
            val alreadyEnabled = configuration.hosts.exceptions.any {
                it.title == EXCEPTION_TITLE
            }
            if (!alreadyEnabled) {
                var added = false
                domains.forEach { domain ->
                    if (configuration.hosts.exceptions.none {
                            it.data.equals(domain, ignoreCase = true) &&
                                it.state == HostState.ALLOW
                        }
                    ) {
                        configuration.hosts.exceptions.add(
                            HostException(
                                title = EXCEPTION_TITLE,
                                data = domain,
                                state = HostState.ALLOW,
                            )
                        )
                        added = true
                    }
                }
                if (added) configuration.save()
            }
        } finally {
            prefs.edit().putBoolean(KEY_DEFAULT_ENABLED, true).apply()
        }
    }

    val domains: List<String> = listOf(
        // Banking, cards and payments
        "paypal.com",
        "paypalobjects.com",
        "stripe.com",
        "stripe.network",
        "adyen.com",
        "checkout.com",
        "klarna.com",
        "satispay.com",
        "posteitaliane.it",
        "poste.it",
        "bancoposta.it",
        "intesasanpaolo.com",
        "unicredit.it",
        "unicreditgroup.eu",
        "fineco.it",
        "bnl.it",
        "mps.it",
        "bpmed.it",
        "credit-agricole.it",
        "cariparma.it",
        "nexi.it",
        "nexigroup.com",
        "mastercard.com",
        "mastercard.us",
        "visa.com",
        "visa.it",
        "americanexpress.com",
        "discover.com",
        "wise.com",
        "revolut.com",
        "n26.com",
        "bunq.com",
        "monzo.com",
        "curve.com",
        "squareup.com",
        "braintreegateway.com",
        "authorize.net",
        "2checkout.com",
        "mollie.com",
        "worldpay.com",
        "sagepay.com",
        "payoneer.com",
        "skrill.com",
        "neteller.com",
        "3dsecure.io",
        "arcot.com",
        "cardinalcommerce.com",
        "modirum.com",
        "netcetera.com",
        "plaid.com",
        "yodlee.com",
        "finicity.com",

        // Italian public administration and digital identity
        "gov.it",
        "spid.gov.it",
        "posteid.poste.it",
        "agid.gov.it",
        "inps.it",
        "agenziaentrate.gov.it",
        "anagrafenazionale.interno.it",
        "cie.gov.it",
        "pagoinpa.gov.it",

        // App stores and platform updates
        "play.google.com",
        "android.com",
        "gvt1.com",
        "gvt2.com",
        "gvt3.com",
        "apps.apple.com",
        "itunes.apple.com",
        "mzstatic.com",

        // Push notifications and connectivity checks
        "push.apple.com",
        "gateway.push.apple.com",
        "mtalk.google.com",
        "android.clients.google.com",
        "fcm.googleapis.com",
        "firebaseinstallations.googleapis.com",
        "connectivitycheck.gstatic.com",
        "connectivitycheck.android.com",
        "clients3.google.com",
        "captive.apple.com",
        "msftconnecttest.com",
        "msftncsi.com",

        // Identity / productivity login flows
        "login.microsoftonline.com",
        "login.live.com",
        "live.com",
        "outlook.com",
        "office.com",
        "office365.com",
        "sharepoint.com",
        "onedrive.com",
        "accounts.google.com",
        "myaccount.google.com",
        "appleid.apple.com"
    ).distinct().sorted()
}
