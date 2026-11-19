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

package com.buzzqw.bbdns.data.service

import com.buzzqw.bbdns.core.diagnostics.BatteryDiagnostics
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.service.utils.manager.FirewallManager
import com.buzzqw.bbdns.service.utils.manager.FirewallMode
import com.buzzqw.bbdns.service.utils.manager.ProtectionState
import com.buzzqw.bbdns.service.utils.manager.VpnManager
import com.buzzqw.bbdns.service.utils.notifications.ProtectionDownNotification
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic watchdog: if the user had protection enabled but the VPN service is
 * no longer running (killed by the system or crashed), start it again.
 *
 * The common case (protection is up, nothing to do) returns before touching
 * DataStore, and every run reports its outcome to the battery diagnostics as
 * `down=0/1`, so an interrupted protection is visible in an exported log.
 */
@HiltWorker
class ProtectionWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firewallManager: FirewallManager,
    private val preferencesUseCases: PreferencesUseCases
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Fast path: the service marks this as soon as it is up. Nothing to
        // read, nothing to write, nothing to restart.
        if (ProtectionState.isRunning) {
            BatteryDiagnostics.event("watchdog_run", "down=0", applicationContext)
            return Result.success()
        }

        BatteryDiagnostics.event("watchdog_run", "down=1", applicationContext)
        return try {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                    if (!settings.protectionEnabled) {
                        // The user turned protection off: stop the periodic
                        // check instead of waking the process for nothing.
                        BatteryDiagnostics.event("watchdog_run", "reason=disabled", applicationContext)
                        ProtectionWatchdog.cancel(applicationContext)
                        ProtectionDownNotification.clear(applicationContext)
                        return@fold
                    }
                    if (!VpnManager.permissionChecker(applicationContext)) {
                        Logger.warn("Protection watchdog: VPN permission missing, cannot restart")
                        BatteryDiagnostics.event("watchdog_run", "reason=no_permission", applicationContext)
                        ProtectionDownNotification.show(applicationContext)
                        return@fold
                    }
                    Logger.info("Protection watchdog: protection down, restarting it")
                    firewallManager.setFirewallMode(FirewallMode.VPN)
                    firewallManager.startFirewall()
                    BatteryDiagnostics.event("protection_restored", context = applicationContext)
                },
                ifFailure = { error ->
                    Logger.error("Protection watchdog: failed to read settings: ${error.message}")
                }
            )
            Result.success()
        } catch (e: IllegalStateException) {
            // Android 8+ forbids starting services from the background. Retrying
            // would only loop: wait for the next periodic run (or the system
            // Always-on VPN) instead.
            Logger.warn("Protection watchdog: background start not allowed: ${e.message}")
            BatteryDiagnostics.event("watchdog_run", "reason=background_denied", applicationContext)
            ProtectionDownNotification.show(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Logger.error("Protection watchdog error: ${e.message}", e)
            BatteryDiagnostics.event("watchdog_run", "reason=error", applicationContext)
            Result.retry()
        }
    }
}
