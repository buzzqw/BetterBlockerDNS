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

package com.buzzqw.bbdns.service.utils.notifications

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.core.utils.NotificationUtils
import com.buzzqw.bbdns.core.utils.extensions.hasNotificationsPermission
import com.buzzqw.bbdns.presentation.MainActivity

/**
 * Warns the user when protection is not running and the watchdog could not
 * restore it by itself (VPN permission withdrawn, background start blocked or
 * start failed). Without it the phone stays silently unfiltered until the app
 * is opened again.
 *
 * The same notification id is reused, so repeated watchdog runs update the
 * warning instead of stacking it, and [clear] removes it as soon as the VPN
 * service starts again.
 */
object ProtectionDownNotification {
    private const val CHANNEL_ID = "protection_alerts"
    private const val NOTIFICATION_ID = 3

    @SuppressLint("MissingPermission")
    fun show(context: Context) {
        if (!context.hasNotificationsPermission()) return
        try {
            val channelId = NotificationUtils.createNotificationChannel(
                context = context,
                channelId = CHANNEL_ID,
                channelName = R.string.notification_protection_channel,
                channelDescription = R.string.notification_protection_channel_desc,
                importance = NotificationManager.IMPORTANCE_DEFAULT,
            )
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(context.getString(R.string.notification_protection_down_title))
                .setContentText(context.getString(R.string.notification_protection_down_desc))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.notification_protection_down_desc))
                )
                .setContentIntent(contentIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                // Re-posted every watchdog run: refresh it, do not alert again.
                .setOnlyAlertOnce(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Logger.error("Cannot show protection down notification: ${e.message}")
        }
    }

    /** Protection is running again: the warning is no longer true. */
    fun clear(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Logger.error("Cannot clear protection down notification: ${e.message}")
        }
    }
}
