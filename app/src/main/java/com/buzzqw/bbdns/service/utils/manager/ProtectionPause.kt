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
import com.buzzqw.bbdns.service.widget.BbdsWidgetProvider

/**
 * Temporary pause of DNS blocking. The VPN keeps running: only the DNS rule
 * is switched off, and a timer owned by the service process switches it back
 * on, so the pause ends even when the UI is closed.
 *
 * The deadline is also saved in private preferences: if Android kills the
 * service during a pause, the watchdog restarts the VPN and
 * [restore] re-applies the remaining pause instead of silently turning
 * blocking back on. The pause is forgotten only when the user turns
 * protection off.
 */
object ProtectionPause {
    const val ACTION_PAUSE = "com.buzzqw.bbdns.action.PAUSE_PROTECTION"
    const val ACTION_RESUME = "com.buzzqw.bbdns.action.RESUME_PROTECTION"
    const val EXTRA_MINUTES = "minutes"

    /** Durations offered by the home and the notification, in minutes. */
    val PRESETS = listOf(5, 15, 60)

    private const val PREFS = "bbdns_protection_pause"
    private const val KEY_PAUSED_UNTIL = "paused_until"

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
        val until = System.currentTimeMillis() + minutes * 60_000L
        context?.applicationContext?.let {
            appContext = it
            persist(it, until)
        }
        setBlocking = setDnsBlocking
        setDnsBlocking(false)
        ProtectionState.setPausedUntil(until)
        handler.removeCallbacks(resumeTask)
        handler.postDelayed(resumeTask, minutes * 60_000L)
        Logger.info("Protection paused for $minutes min")
        refreshSurfaces()
    }

    /** End the pause now. Does nothing when protection is not paused. */
    fun resume() {
        handler.removeCallbacks(resumeTask)
        if (!ProtectionState.isPaused) return
        ProtectionState.setPausedUntil(0L)
        setBlocking?.invoke(true)
        setBlocking = null
        appContext?.let { persist(it, 0L) }
        Logger.info("Protection resumed")
        refreshSurfaces()
    }

    /**
     * Resume when the deadline already passed. The main-thread timer does not
     * advance during deep sleep, so the service also calls this periodically.
     */
    fun resumeIfExpired() {
        val until = ProtectionState.pausedUntil.value
        if (until > 0L && System.currentTimeMillis() >= until) resume()
    }

    /**
     * Re-apply a pause saved before the process was killed. Call it once the
     * VPN rules are ready, right after a session starts.
     */
    fun restore(context: Context?, setDnsBlocking: (Boolean) -> Unit) {
        val ctx = context?.applicationContext ?: return
        appContext = ctx
        val saved = read(ctx)
        if (saved <= System.currentTimeMillis()) {
            if (saved != 0L) persist(ctx, 0L)
            return
        }
        setBlocking = setDnsBlocking
        setDnsBlocking(false)
        ProtectionState.setPausedUntil(saved)
        handler.removeCallbacks(resumeTask)
        handler.postDelayed(resumeTask, saved - System.currentTimeMillis())
        Logger.info("Protection pause restored, resumes at ${java.util.Date(saved)}")
        refreshSurfaces()
    }

    /** Drop the in-process timer and state, keeping the saved deadline. */
    fun resetSession() {
        handler.removeCallbacks(resumeTask)
        setBlocking = null
        ProtectionState.setPausedUntil(0L)
    }

    /** Forget the pause completely. Used when the user stops protection. */
    fun clear(context: Context?) {
        context?.applicationContext?.let { appContext = it }
        handler.removeCallbacks(resumeTask)
        setBlocking = null
        ProtectionState.setPausedUntil(0L)
        appContext?.let { persist(it, 0L) }
    }

    private fun persist(context: Context, until: Long) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_PAUSED_UNTIL, until)
                .apply()
        } catch (e: Exception) {
            Logger.warn("Cannot save the pause deadline: ${e.message}")
        }
    }

    private fun read(context: Context): Long = try {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_PAUSED_UNTIL, 0L)
    } catch (e: Exception) {
        0L
    }

    /** Keep the Quick Settings tile and the home screen widget in sync. */
    private fun refreshSurfaces() {
        val context = appContext ?: return
        try {
            TileService.requestListeningState(
                context,
                ComponentName(context, DnsQuickSettingsTile::class.java)
            )
        } catch (e: Exception) {
            Logger.debug("Cannot refresh the Quick Settings tile: ${e.message}")
        }
        BbdsWidgetProvider.updateAll(context)
    }
}
