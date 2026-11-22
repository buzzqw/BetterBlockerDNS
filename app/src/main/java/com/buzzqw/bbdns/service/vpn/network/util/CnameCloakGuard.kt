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

import com.buzzqw.bbdns.service.vpn.network.transport.dns.DnsAnswers

/** A queried name that resolves through a blocked alias. */
data class CnameCloak(val domain: String, val target: String)

/**
 * Detects CNAME cloaking: a tracker served from the site's own domain.
 *
 * The site answers `metrics.example.com` with a CNAME to
 * `tracker.adnetwork.net`. The queried name is not on any blocklist (it
 * belongs to the site), so a DNS filter that only looks at the question lets
 * the tracker through. Checking every alias in the answer closes that hole
 * without decrypting anything.
 *
 * An explicit allow always wins, on the queried name and on the alias: a user
 * who allowed a domain means it, and an alias it points to is part of that
 * domain's answer.
 */
object CnameCloakGuard {

    /**
     * Returns the first blocked alias in [response], or null when the answer
     * is clean, the queried name is already handled by the normal rule chain,
     * or the user allowed the name.
     *
     * @param isBlocked true when the domain is blocked by lists or custom rules.
     * @param isExplicitlyAllowed true for an allow rule the user (or a list)
     *   wrote on purpose, which [isBlocked] alone cannot tell from "not listed".
     */
    fun find(
        response: ByteArray,
        queried: String,
        isBlocked: (String) -> Boolean,
        isExplicitlyAllowed: (String) -> Boolean = { false },
    ): CnameCloak? {
        val domain = normalize(queried)
        if (domain.isEmpty()) return null
        // Already blocked: the normal path answers before the response exists.
        if (isBlocked(domain)) return null
        // The user allowed the site: its answers are theirs to receive.
        if (isExplicitlyAllowed(domain)) return null

        for (target in DnsAnswers.cnameTargets(response)) {
            if (target == domain) continue
            if (isExplicitlyAllowed(target)) continue
            if (isBlocked(target)) return CnameCloak(domain, target)
        }
        return null
    }

    private fun normalize(name: String): String = name.trim().trimEnd('.').lowercase()
}
