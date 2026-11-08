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

package com.buzzqw.bbdns.service.utils.manager

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.service.tiles.DnsQuickSettingsTile

/**
 * Temporary pause of DNS blocking. The VPN keeps running: only the DNS rule
 * is switched off, and a timer owned by the service process switches it back
 * on, so the pause ends even when the UI is closed. A VPN restart clears it.
 */
object ProtectionPause {
    const val ACTION_PAUSE = "com.buzzqw.bbdns.action.PAUSE_PROTECTION"
    const val ACTION_RESUME = "com.buzzqw.bbdns.action.RESUME_PROTECTION"
    const val EXTRA_MINUTES = "minutes"

    /** Durations offered by the home and the notification, in minutes. */
    val PRESETS = listOf(5, 15, 60)

    private val handler = Handler(Looper.getMainLooper())
    private val resumeTask = Runnable { resume() }

    @Volatile
    private var setBlocking: ((Boolean) -> Unit)? = null

    @Volatile
    private var appContext: Context? = null

    /**
     * Disable DNS blocking for [minutes]. [setDnsBlocking] is kept to switch
     * blocking back on when the pause ends.
     */
    fun pause(context: Context?, minutes: Int, setDnsBlocking: (Boolean) -> Unit) {
        if (minutes <= 0) return
        context?.applicationContext?.let { appContext = it }
        setBlocking = setDnsBlocking
        val durationMs = minutes * 60_000L
        setDnsBlocking(false)
        ProtectionState.setPausedUntil(System.currentTimeMillis() + durationMs)
        handler.removeCallbacks(resumeTask)
        handler.postDelayed(resumeTask, durationMs)
        Logger.info("Protection paused for $minutes min")
        refreshTile()
    }

    /** End the pause now. Does nothing when protection is not paused. */
    fun resume() {
        handler.removeCallbacks(resumeTask)
        if (!ProtectionState.isPaused) return
        ProtectionState.setPausedUntil(0L)
        setBlocking?.invoke(true)
        setBlocking = null
        Logger.info("Protection resumed")
        refreshTile()
    }

    /**
     * Resume when the deadline already passed. The main-thread timer does not
     * advance during deep sleep, so the service also calls this periodically.
     */
    fun resumeIfExpired() {
        val until = ProtectionState.pausedUntil.value
        if (until > 0L && System.currentTimeMillis() >= until) resume()
    }

    private fun refreshTile() {
        val context = appContext ?: return
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, DnsQuickSettingsTile::class.java)
            )
        } catch (e: Exception) {
            Logger.debug("Cannot refresh the Quick Settings tile: ${e.message}")
        }
    }
}
