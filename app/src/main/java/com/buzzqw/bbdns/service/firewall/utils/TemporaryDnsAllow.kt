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

import java.util.concurrent.ConcurrentHashMap

/**
 * Domains the user let through from the local notice page, for a few minutes.
 *
 * Living here (and not in the notice page) means the DNS rule and the page
 * agree on what "continue" means: the browser may still have the private
 * address cached, but the next lookup already resolves normally, and the
 * choice disappears when protection stops.
 */
object TemporaryDnsAllow {

    /** A parent domain covers its subdomains, like an allow rule. */
    private const val MAX_ENTRIES = 256

    private val allowed = ConcurrentHashMap<String, Long>()

    /**
     * Allows [domain] and its subdomains until [minutes] have passed.
     * An expired entry is simply replaced.
     */
    fun allow(domain: String, minutes: Int, now: Long = System.currentTimeMillis()) {
        val host = normalize(domain)
        if (host.isEmpty() || minutes <= 0) return
        purge(now)
        if (allowed.size >= MAX_ENTRIES && !allowed.containsKey(host)) {
            // Keep the newest choices instead of growing without bound: an
            // app could otherwise fill the map through the notice page.
            allowed.entries.minByOrNull { it.value }?.let { allowed.remove(it.key) }
        }
        allowed[host] = now + minutes * 60_000L
    }

    /** True while [domain] or one of its parents is allowed. */
    fun isAllowed(domain: String, now: Long = System.currentTimeMillis()): Boolean {
        val host = normalize(domain)
        if (host.isEmpty() || allowed.isEmpty()) return false
        if (alive(host, now)) return true
        var dot = host.indexOf('.')
        while (dot != -1 && dot < host.length - 1) {
            val parent = host.substring(dot + 1)
            // A bare TLD is never a temporary allow.
            if (parent.indexOf('.') == -1) break
            if (alive(parent, now)) return true
            dot = host.indexOf('.', dot + 1)
        }
        return false
    }

    /** Ends every temporary allow, e.g. when protection restarts. */
    fun clear() {
        allowed.clear()
    }

    fun size(): Int = allowed.size

    private fun alive(host: String, now: Long): Boolean {
        val expiresAt = allowed[host] ?: return false
        if (expiresAt > now) return true
        allowed.remove(host, expiresAt)
        return false
    }

    private fun purge(now: Long) {
        allowed.entries.removeIf { it.value <= now }
    }

    private fun normalize(name: String): String = name.trim().trimEnd('.').lowercase()
}
