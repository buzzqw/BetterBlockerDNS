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

import android.os.SystemClock
import android.util.LruCache
import com.buzzqw.bbdns.service.firewall.model.FireWallModel

/**
 * Remembers the owning UID of every network flow.
 *
 * The firewall used to resolve the UID of each packet, which is very costly
 * (a hidden framework call and, on older devices, a scan of /proc/net). A flow
 * is identified by its 4-tuple, so resolving it once is enough. The first
 * packet of a flow is also the only one that has to be written to the logs:
 * the following packets belong to a connection already recorded.
 */
object FlowUidCache {
    private const val MAX_FLOWS = 512
    private const val FLOW_IDLE_MS = 5 * 60 * 1000L

    data class FlowLookup(val uid: Int, val firstPacket: Boolean)

    private data class FlowKey(
        val protocol: Byte,
        val sourceIp: String,
        val sourcePort: Int,
        val destinationIp: String,
        val destinationPort: Int,
    )

    private class FlowEntry(val uid: Int, var lastSeen: Long)

    private val flows = object : LruCache<FlowKey, FlowEntry>(MAX_FLOWS) {}

    /**
     * Return the cached UID for [packet], calling [lookupUid] only for a new
     * (or expired) flow. The caller must write the packet to the logs only
     * when [FlowLookup.firstPacket] is true.
     */
    fun resolve(packet: FireWallModel, lookupUid: () -> Int): FlowLookup {
        val key = FlowKey(
            protocol = packet.protocol,
            sourceIp = packet.sourceIP,
            sourcePort = packet.sourcePort,
            destinationIp = packet.destinationIP,
            destinationPort = packet.destinationPort,
        )
        val now = SystemClock.elapsedRealtime()

        synchronized(flows) {
            val entry = flows.get(key)
            if (entry != null && now - entry.lastSeen <= FLOW_IDLE_MS) {
                entry.lastSeen = now
                return FlowLookup(entry.uid, firstPacket = false)
            }
        }

        val uid = lookupUid()
        synchronized(flows) {
            flows.put(key, FlowEntry(uid, SystemClock.elapsedRealtime()))
        }
        return FlowLookup(uid, firstPacket = true)
    }

    fun clear() {
        synchronized(flows) {
            flows.evictAll()
        }
    }
}
