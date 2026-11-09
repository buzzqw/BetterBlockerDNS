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

package com.buzzqw.bbdns.core.utils.extensions

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * Reverse DNS answers are cached for a while: resolving the same address once
 * per connection (or worse, once per packet) is a needless radio and CPU cost.
 * An empty string means "no hostname", so failures are cached too.
 */
private const val HOSTNAME_CACHE_ENTRIES = 256
private const val HOSTNAME_CACHE_TTL_MS = 10 * 60 * 1000L

private class HostnameEntry(val hostname: String, val expiresAt: Long)

private val hostnameCache = object : LruCache<String, HostnameEntry>(HOSTNAME_CACHE_ENTRIES) {}

/**
 * Extension function to resolve an IP address to a hostname.
 *
 * @return the hostname as a String, or null if the resolution fails.
 */
suspend fun String.resolveIpToHostname(): String? {
    val address = this
    val now = System.currentTimeMillis()
    synchronized(hostnameCache) {
        hostnameCache.get(address)?.let { entry ->
            if (entry.expiresAt > now) {
                return entry.hostname.ifEmpty { null }
            }
            hostnameCache.remove(address)
        }
    }

    val hostname = withContext(Dispatchers.IO) {
        try {
            val resolved = InetAddress.getByName(address)
            if (resolved.hostName == address) null else resolved.hostName
        } catch (e: Exception) {
            null
        }
    }

    synchronized(hostnameCache) {
        hostnameCache.put(
            address,
            HostnameEntry(hostname ?: "", System.currentTimeMillis() + HOSTNAME_CACHE_TTL_MS)
        )
    }
    return hostname
}