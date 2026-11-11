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

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.net.Uri
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.provider.Settings
import com.buzzqw.bbdns.core.logging.Logger


/**
 * Hidden Android action for the Private DNS page (not part of the public SDK
 * constants, hence the literal string). Some OEM ROMs do not expose it.
 */
private const val ACTION_PRIVATE_DNS_SETTINGS = "android.settings.PRIVATE_DNS_SETTINGS"

val Context.notificationManagerCompat get() = NotificationManagerCompat.from(this)

fun Context.doIfNotificationsAllowed(block: NotificationManagerCompat.() -> Unit) = if (hasNotificationsPermission())
    block(notificationManagerCompat)
else
    Unit

@SuppressLint("InlinedApi")
fun Context.hasNotificationsPermission() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
else
    true

fun Context.isFingerprintSupported(): Boolean {
    val biometricManager = BiometricManager.from(this)
    return when (biometricManager.canAuthenticate(BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> true
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false // Hardware exists but no fingerprints enrolled
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> false
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> false
        BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> false
        else -> false
    }
}

/**
 * Opens the Android Private DNS page, falling back to the network settings and
 * finally to the main Settings screen. Several OEMs (MIUI/HyperOS, some One UI
 * builds, ...) do not export the Private DNS page, so a plain startActivity
 * would just fail silently.
 *
 * @return true when a Settings activity was launched.
 */
fun Context.openPrivateDnsSettings(): Boolean {
    val actions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(ACTION_PRIVATE_DNS_SETTINGS)
        }
        add(Settings.ACTION_WIRELESS_SETTINGS)
        add(Settings.ACTION_SETTINGS)
    }

    for (action in actions) {
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolvable = try {
            intent.resolveActivity(packageManager) != null
        } catch (e: Exception) {
            Logger.warn("Cannot resolve $action: ${e.message}")
            false
        }
        if (!resolvable) continue
        try {
            startActivity(intent)
            return true
        } catch (e: Exception) {
            Logger.warn("Cannot start $action: ${e.message}")
        }
    }

    Logger.error("No Settings activity available to open Private DNS")
    return false
}

fun Context.requestDisableBatteryOptimization() {
    val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // Open the system list of apps instead of the direct exemption
            // dialog: REQUEST_IGNORE_BATTERY_OPTIMIZATIONS is restricted by
            // Google Play, and this screen reaches the same setting.
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Logger.warn("No battery optimisation list, opening app settings")
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        } else {
            Logger.info("Battery optimization is already disabled for this app.")
        }
    } else {
        Logger.error("Battery optimization is not supported on this device.")
    }
}