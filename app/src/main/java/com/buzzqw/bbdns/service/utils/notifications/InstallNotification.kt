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
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.core.utils.NotificationUtils as CoreNotificationUtils
import com.buzzqw.bbdns.core.utils.extensions.doIfNotificationsAllowed
import com.buzzqw.bbdns.core.utils.extensions.getApplicationName
import com.buzzqw.bbdns.domain.model.Application
import com.buzzqw.bbdns.domain.model.Settings
import com.buzzqw.bbdns.domain.usecase.application.ApplicationUseCases
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.service.utils.manager.FirewallManager
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants
import com.buzzqw.bbdns.service.vpn.service.VpnConnectionClient
import com.buzzqw.bbdns.service.vpn.service.VpnConnectionServer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
suspend fun Service.showInstallNotification(
    packageName: String,
    applicationUseCases: ApplicationUseCases,
    preferencesUseCases: PreferencesUseCases,
    firewallManager: FirewallManager,
    application: Application? = null
) {
    val channelID = CoreNotificationUtils.createNotificationChannel(
        context = this,
        channelId = "install_channel",
        channelName = R.string.notification_install_channel,
        channelDescription = R.string.notification_install_channel_desc,
        importance = NotificationManager.IMPORTANCE_MAX,
    )

    coroutineScope {
        launch {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                    val applicationToUse = application ?: createApplication(packageName, settings)
                    applicationToUse?.let { app ->
                        applicationUseCases.addApplication.execute(app).fold(
                            ifSuccess = {
                                firewallManager.updateFirewallRules(app)
                                if (settings.sendNotificationOnInstall) {
                                    showNotification(packageName, channelID, app)
                                }
                            }
                        )
                    } ?: run {
                        Logger.error("Failed to create application for package $packageName")
                    }
                }
            )
        }
    }
}

private fun Service.createApplication(packageName: String, settings: Settings): Application? {
    return try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        Application(
            packageID = packageName,
            uid = info.uid,
            internetAccess = settings.wiFiDefault,
            cellularAccess = settings.cellularDefault,
            systemApp = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
        )
    } catch (e: Exception) {
        Logger.error("Failed to get application info for package $packageName: ${e.message}", e)
        null
    }
}

@SuppressLint("MissingPermission")
private fun Service.showNotification(
    packageName: String,
    channelID: String,
    application: Application
) {
    doIfNotificationsAllowed {
        val notification = createInstallNotification(packageName, channelID, application)
        notify("Package Install", 2, notification)
    }
}

private fun Service.createInstallNotification(
    packageName: String,
    channelID: String,
    application: Application
): Notification {
    val serviceClass = VpnConnectionServer::class.java

    return NotificationCompat.Builder(this, channelID).apply {
        setColor(Color.RED)
        setContentTitle(application.getApplicationName(packageManager))
        setContentText(getString(R.string.notification_install_message))
        setSmallIcon(android.R.drawable.stat_sys_download_done)
        setPriority(NotificationCompat.PRIORITY_HIGH)
        setColorized(true)

        addAction(createToggleWifiAction(packageName, serviceClass, application))
        addAction(createToggleCellularAction(packageName, serviceClass, application))
    }.build()
}

private fun Service.createToggleWifiAction(
    packageName: String,
    serviceClass: Class<*>,
    application: Application
): NotificationCompat.Action {
    val toggleWifiIntent = createPendingIntent(
        packageName,
        serviceClass,
        NetworkConstants.ACTION_TOGGLE_WIFI
    )
    return NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_close_clear_cancel,
        "${getEnableDisableText(application.internetAccess)} Wifi",
        toggleWifiIntent
    ).build()
}

private fun Service.createToggleCellularAction(
    packageName: String,
    serviceClass: Class<*>,
    application: Application
): NotificationCompat.Action {
    val toggleCellularIntent = createPendingIntent(
        packageName,
        serviceClass,
        NetworkConstants.ACTION_TOGGLE_CELLURAL
    )
    return NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_close_clear_cancel,
        "${getEnableDisableText(application.cellularAccess)} Cellular",
        toggleCellularIntent
    ).build()
}

private fun Service.createPendingIntent(
    packageName: String,
    serviceClass: Class<*>,
    action: String
): PendingIntent {
    return PendingIntent.getService(
        this,
        packageName.hashCode() + action.hashCode(),
        Intent(this, serviceClass).apply {
            this.action = action
            putExtra("packageName", packageName)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun Service.getEnableDisableText(isEnabled: Boolean): String {
    return if (isEnabled) this.getString(R.string.common_disable) else this.getString(R.string.common_enable)
}