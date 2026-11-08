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

package com.buzzqw.bbdns.service.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.core.utils.NumberFormatter
import com.buzzqw.bbdns.presentation.MainActivity
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters
import com.buzzqw.bbdns.service.utils.manager.ProtectionPause
import com.buzzqw.bbdns.service.utils.manager.ProtectionState
import com.buzzqw.bbdns.service.utils.manager.VpnManager
import com.buzzqw.bbdns.service.vpn.service.VpnConnectionServer
import java.util.Date

/**
 * Home screen widget: shows whether protection is running (or paused) and how
 * many requests were blocked today. The button starts or stops protection, or
 * ends a pause, without opening the app.
 */
class BbdsWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_TOGGLE = "com.buzzqw.bbdns.widget.TOGGLE"

        /** Redraw every widget instance of this provider. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, BbdsWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val provider = BbdsWidgetProvider()
            ids.forEach { provider.render(context, manager, it) }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                toggleProtection(context)
                updateAll(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun toggleProtection(context: Context) {
        try {
            when {
                ProtectionState.isPaused -> resumeProtection(context)
                ProtectionState.isRunning -> VpnManager.stop(context)
                VpnManager.permissionChecker(context) -> VpnManager.start(context)
                else -> openApp(context)
            }
        } catch (e: Exception) {
            // Android 12+ can refuse a background foreground-service start.
            Logger.warn("Widget cannot change protection: ${e.message}")
            openApp(context)
        }
    }

    private fun resumeProtection(context: Context) {
        val intent = Intent(context, VpnConnectionServer::class.java).apply {
            action = ProtectionPause.ACTION_RESUME
        }
        context.startService(intent)
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_bbdns)

        val pausedUntil = ProtectionState.pausedUntil.value
        val now = System.currentTimeMillis()
        val paused = pausedUntil > now
        val running = ProtectionState.isRunning

        val status: String
        val details: String
        val button: String
        when {
            paused -> {
                status = context.getString(R.string.widget_status_paused)
                details = context.getString(
                    R.string.widget_paused_until,
                    DateFormat.getTimeFormat(context).format(Date(pausedUntil))
                )
                button = context.getString(R.string.widget_resume)
            }
            running -> {
                status = context.getString(R.string.widget_status_on)
                details = context.getString(
                    R.string.widget_blocked_today,
                    NumberFormatter.formatCount(blockedToday(context))
                )
                button = context.getString(R.string.widget_turn_off)
            }
            else -> {
                status = context.getString(R.string.widget_status_off)
                details = context.getString(R.string.widget_off_hint)
                button = context.getString(R.string.widget_turn_on)
            }
        }

        views.setTextViewText(R.id.widget_status, status)
        views.setTextViewText(R.id.widget_stats, details)
        views.setTextViewText(R.id.widget_toggle, button)

        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
        views.setOnClickPendingIntent(
            R.id.widget_toggle,
            PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, BbdsWidgetProvider::class.java).setAction(ACTION_TOGGLE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        manager.updateAppWidget(appWidgetId, views)
    }

    private fun blockedToday(context: Context): Long {
        FirewallCounters.init(context)
        return FirewallCounters.history(1).lastOrNull()?.blocked ?: 0L
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openApp(context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Logger.warn("Widget cannot open the app: ${e.message}")
        }
    }
}
