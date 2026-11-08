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

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Live statistics of the current VPN session: allowed/blocked DNS queries,
 * a per-minute activity timeline, the most blocked domains and the latest
 * blocked ones. They power the home dashboard and the foreground
 * notification and do not depend on log persistence being enabled.
 *
 * It also keeps daily allowed/blocked totals for the last [HISTORY_DAYS]
 * days, saved to private preferences at most once a minute and when the VPN
 * stops (see [init] and [flush]), so the history survives app restarts.
 */
object FirewallCounters {
    const val TIMELINE_MINUTES = 30
    private const val TOP_DOMAINS_LIMIT = 2_000
    private const val RECENT_LIMIT = 20

    private val allowed = AtomicLong(0)
    private val blocked = AtomicLong(0)

    private val blockedByDomain = ConcurrentHashMap<String, AtomicLong>()

    /** Per-app counters of the session: uid to [queries, blocked]. */
    private val byUid = ConcurrentHashMap<Int, LongArray>()

    /**
     * Average size of what a blocked request would have downloaded (ad
     * scripts, images, tracker beacons). A deliberately conservative figure
     * used only for the "data saved" estimate.
     */
    const val ESTIMATED_BYTES_PER_BLOCK = 20L * 1024

    private val lock = Any()
    private val recent = ArrayDeque<BlockedEvent>()
    private val timelineAllowed = IntArray(TIMELINE_MINUTES)
    private val timelineBlocked = IntArray(TIMELINE_MINUTES)
    private var timelineMinute = 0L

    const val HISTORY_DAYS = 30
    private const val DAILY_PREFS = "bbdns_daily_stats"
    private const val DAILY_KEY = "days"
    private const val FLUSH_INTERVAL_MS = 60_000L

    private val dailyLock = Any()
    /** Calendar day as yyyyMMdd to [allowed, blocked]. Guarded by [dailyLock]. */
    private val daily = HashMap<Int, LongArray>()
    private var dailyDirty = false
    private var lastFlush = 0L
    private var todayKey = 0
    private var todayStart = 0L
    private var todayEnd = 0L

    @Volatile
    private var prefs: SharedPreferences? = null

    data class BlockedEvent(val domain: String, val timestamp: Long)

    /** Totals of one calendar day; [dayStart] is local midnight. */
    data class DayStats(val dayStart: Long, val allowed: Long, val blocked: Long) {
        val total: Long get() = allowed + blocked
    }

    data class SessionDetails(
        /** Oldest first, one bucket per minute, the last one is the current minute. */
        val allowedPerMinute: List<Int>,
        val blockedPerMinute: List<Int>,
        val topBlocked: List<Pair<String, Long>>,
        val recentBlocked: List<BlockedEvent>,
    )

    fun reset() {
        allowed.set(0)
        blocked.set(0)
        blockedByDomain.clear()
        byUid.clear()
        synchronized(lock) {
            recent.clear()
            timelineAllowed.fill(0)
            timelineBlocked.fill(0)
            timelineMinute = currentMinute()
        }
    }

    /**
     * Load the saved daily history. Safe to call more than once; counts
     * recorded before the first call are kept.
     */
    fun init(context: Context) {
        if (prefs != null) return
        synchronized(dailyLock) {
            if (prefs != null) return
            val preferences = context.applicationContext
                .getSharedPreferences(DAILY_PREFS, Context.MODE_PRIVATE)
            try {
                preferences.getString(DAILY_KEY, null)?.let { raw ->
                    val json = JSONObject(raw)
                    json.keys().forEach { key ->
                        val day = key.toIntOrNull() ?: return@forEach
                        val values = json.optJSONArray(key) ?: return@forEach
                        val bucket = daily.getOrPut(day) { LongArray(2) }
                        bucket[0] += values.optLong(0)
                        bucket[1] += values.optLong(1)
                    }
                }
            } catch (e: Exception) {
                // A corrupted history must never break the VPN.
                daily.clear()
            }
            prefs = preferences
        }
    }

    /** Save the daily history now if it changed since the last save. */
    fun flush() {
        val preferences = prefs ?: return
        val json = synchronized(dailyLock) {
            if (!dailyDirty) return
            serializeDailyLocked(System.currentTimeMillis())
        }
        preferences.edit().putString(DAILY_KEY, json).apply()
    }

    /**
     * Daily totals of the last [days] days, oldest first, today last. Days
     * without traffic are returned with zero counts.
     */
    fun history(days: Int = HISTORY_DAYS): List<DayStats> {
        val count = days.coerceIn(1, HISTORY_DAYS)
        val calendar = Calendar.getInstance().apply { startOfDay(this) }
        calendar.add(Calendar.DAY_OF_YEAR, -(count - 1))
        val result = ArrayList<DayStats>(count)
        synchronized(dailyLock) {
            repeat(count) {
                val bucket = daily[dayKey(calendar)]
                result.add(DayStats(calendar.timeInMillis, bucket?.get(0) ?: 0L, bucket?.get(1) ?: 0L))
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return result
    }

    fun record(isAllowed: Boolean, domain: String? = null, uid: Int = -1) {
        if (isAllowed) allowed.incrementAndGet() else blocked.incrementAndGet()
        if (uid >= 0) {
            val bucket = byUid.getOrPut(uid) { LongArray(2) }
            synchronized(bucket) {
                bucket[0]++
                if (!isAllowed) bucket[1]++
            }
        }
        val now = System.currentTimeMillis()
        recordDaily(isAllowed, now)
        val cleanDomain = domain?.trim()?.trimEnd('.')?.lowercase()?.takeIf { it.isNotEmpty() }
        if (!isAllowed && cleanDomain != null) {
            val counter = blockedByDomain[cleanDomain]
            when {
                counter != null -> counter.incrementAndGet()
                blockedByDomain.size < TOP_DOMAINS_LIMIT ->
                    blockedByDomain.getOrPut(cleanDomain) { AtomicLong(0) }.incrementAndGet()
            }
        }
        synchronized(lock) {
            advanceTimeline(now / 60_000)
            val slot = TIMELINE_MINUTES - 1
            if (isAllowed) timelineAllowed[slot]++ else timelineBlocked[slot]++
            if (!isAllowed && cleanDomain != null) {
                // Collapse bursts of the same domain into the latest entry.
                if (recent.firstOrNull()?.domain == cleanDomain) recent.removeFirst()
                recent.addFirst(BlockedEvent(cleanDomain, now))
                while (recent.size > RECENT_LIMIT) recent.removeLast()
            }
        }
    }

    /** Apps of the session by blocked requests: uid, queries, blocked. */
    fun topApps(limit: Int = 8): List<Triple<Int, Long, Long>> =
        byUid.entries
            .map { (uid, v) -> synchronized(v) { Triple(uid, v[0], v[1]) } }
            .sortedWith(compareByDescending<Triple<Int, Long, Long>> { it.third }.thenByDescending { it.second })
            .take(limit)

    /** Estimated bytes not downloaded thanks to [blockedCount] blocked requests. */
    fun estimatedSavedBytes(blockedCount: Long): Long = blockedCount * ESTIMATED_BYTES_PER_BLOCK

    /** @return allowed to blocked snapshot. */
    fun snapshot(): Pair<Long, Long> = allowed.get() to blocked.get()

    fun details(topLimit: Int = 5, recentLimit: Int = 5): SessionDetails {
        val top = blockedByDomain.entries
            .map { it.key to it.value.get() }
            .sortedByDescending { it.second }
            .take(topLimit)
        synchronized(lock) {
            advanceTimeline(currentMinute())
            return SessionDetails(
                allowedPerMinute = timelineAllowed.toList(),
                blockedPerMinute = timelineBlocked.toList(),
                topBlocked = top,
                recentBlocked = recent.take(recentLimit),
            )
        }
    }

    private fun currentMinute() = System.currentTimeMillis() / 60_000

    private fun recordDaily(isAllowed: Boolean, now: Long) {
        var json: String? = null
        synchronized(dailyLock) {
            if (now < todayStart || now >= todayEnd) updateToday(now)
            val bucket = daily.getOrPut(todayKey) { LongArray(2) }
            bucket[if (isAllowed) 0 else 1]++
            dailyDirty = true
            if (prefs != null && now - lastFlush >= FLUSH_INTERVAL_MS) {
                json = serializeDailyLocked(now)
            }
        }
        json?.let { prefs?.edit()?.putString(DAILY_KEY, it)?.apply() }
    }

    /** Cache the bounds of the current day. Caller holds [dailyLock]. */
    private fun updateToday(now: Long) {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            startOfDay(this)
        }
        todayKey = dayKey(calendar)
        todayStart = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        todayEnd = calendar.timeInMillis
    }

    /** Drop days older than the history and encode the rest. Caller holds [dailyLock]. */
    private fun serializeDailyLocked(now: Long): String {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            startOfDay(this)
            add(Calendar.DAY_OF_YEAR, -(HISTORY_DAYS - 1))
        }
        val oldestKey = dayKey(calendar)
        daily.keys.removeAll { it < oldestKey }
        val json = JSONObject()
        daily.forEach { (day, values) ->
            json.put(day.toString(), JSONArray().put(values[0]).put(values[1]))
        }
        dailyDirty = false
        lastFlush = now
        return json.toString()
    }

    private fun startOfDay(calendar: Calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }

    private fun dayKey(calendar: Calendar): Int =
        calendar.get(Calendar.YEAR) * 10_000 +
            (calendar.get(Calendar.MONTH) + 1) * 100 +
            calendar.get(Calendar.DAY_OF_MONTH)

    /** Shift the buckets so that the last one is [minute]. Caller holds [lock]. */
    private fun advanceTimeline(minute: Long) {
        val shift = (minute - timelineMinute).coerceAtLeast(0)
        if (shift == 0L) return
        if (shift >= TIMELINE_MINUTES) {
            timelineAllowed.fill(0)
            timelineBlocked.fill(0)
        } else {
            val s = shift.toInt()
            System.arraycopy(timelineAllowed, s, timelineAllowed, 0, TIMELINE_MINUTES - s)
            System.arraycopy(timelineBlocked, s, timelineBlocked, 0, TIMELINE_MINUTES - s)
            timelineAllowed.fill(0, TIMELINE_MINUTES - s, TIMELINE_MINUTES)
            timelineBlocked.fill(0, TIMELINE_MINUTES - s, TIMELINE_MINUTES)
        }
        timelineMinute = minute
    }
}
