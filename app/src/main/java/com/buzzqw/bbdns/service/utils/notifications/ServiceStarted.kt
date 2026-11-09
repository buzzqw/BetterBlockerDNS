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

package com.buzzqw.bbdns.service.utils.notifications

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.core.utils.NotificationUtils
import com.buzzqw.bbdns.domain.model.Settings
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.presentation.MainActivity
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters
import com.buzzqw.bbdns.service.utils.manager.ProtectionPause
import com.buzzqw.bbdns.service.utils.manager.ProtectionState
import com.buzzqw.bbdns.service.vpn.service.VpnConnectionServer
import java.util.Date

@SuppressLint("MissingPermission")
suspend fun Service.showStartNotification(
    preferencesUseCases: PreferencesUseCases,
    context: Context? = null,
    allowed: Long = FirewallCounters.snapshot().first,
    blocked: Long = FirewallCounters.snapshot().second,
    settings: Settings? = null,
) {
    val contextNot = context ?: this
    // The live counter refresh reloads the settings at most every few minutes
    // and passes them in, instead of reading DataStore for every notification.
    val currentSettings = settings ?: preferencesUseCases.loadSettings.execute().fold(
        ifSuccess = { it },
        ifFailure = { error ->
            Logger.error("Failed to load settings: ${error.message}")
            null
        }
    ) ?: return

    val channelID = NotificationUtils.createNotificationChannel(
        context = contextNot,
        channelId = "service_running",
        channelName = R.string.vpn_service_channel_name,
        channelDescription = R.string.vpn_service_channel_description,
        importance = if (currentSettings.permanentNotification) NotificationManager.IMPORTANCE_LOW else NotificationManager.IMPORTANCE_DEFAULT
    )

    val notificationPendingIntent = PendingIntent.getActivity(
        contextNot,
        0,
        Intent(contextNot, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val pausedUntil = ProtectionState.pausedUntil.value
    val pauseAction = if (pausedUntil > 0L) {
        NotificationCompat.Action(
            0,
            contextNot.getString(R.string.notification_action_resume),
            servicePendingIntent(contextNot, ProtectionPause.ACTION_RESUME, 0)
        )
    } else {
        NotificationCompat.Action(
            0,
            contextNot.getString(R.string.notification_action_pause_15),
            servicePendingIntent(contextNot, ProtectionPause.ACTION_PAUSE, 15)
        )
    }

    val notificationNotColorized =
        NotificationCompat.Builder(contextNot, channelID).apply {
            setContentTitle(contextNot.getString(R.string.vpn_service_running))
            setContentText(
                if (pausedUntil > 0L) {
                    contextNot.getString(
                        R.string.notification_paused_text,
                        android.text.format.DateFormat.getTimeFormat(contextNot)
                            .format(Date(pausedUntil))
                    )
                } else {
                    contextNot.getString(
                        R.string.notification_allowed_blocked,
                        allowed,
                        blocked
                    )
                }
            )
            addAction(pauseAction)
            if (currentSettings.permanentNotification) {
                // "Permanent" means quiet and uncoloured: it should not
                // attract attention while the VPN is running.
                setSilent(true)
                setOngoing(true)
                setColorized(false)
                setPriority(NotificationCompat.PRIORITY_LOW)
            } else {
                setColorized(true)
                setPriority(NotificationCompat.PRIORITY_HIGH)
            }
            // The notification is refreshed with live counters: alert
            // only the first time and keep it attached to the service.
            setOnlyAlertOnce(true)
            setOngoing(true)
            setShowWhen(false)
            // BBDNS brand green accent (small icon, app name, actions).
            setColor(ContextCompat.getColor(contextNot, R.color.bbdns_brand))
            setSmallIcon(R.drawable.logo)
            setContentIntent(notificationPendingIntent)
        }.build()
    if (context == null) {
        startForeground(2, notificationNotColorized)
    } else {
        NotificationManagerCompat.from(contextNot).notify(2, notificationNotColorized)
    }
}

/** Intent delivered to the VPN service by a notification action. */
private fun servicePendingIntent(context: Context, action: String, minutes: Int): PendingIntent {
    val intent = Intent(context, VpnConnectionServer::class.java)
        .setAction(action)
        .putExtra(ProtectionPause.EXTRA_MINUTES, minutes)
    return PendingIntent.getService(
        context,
        action.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
