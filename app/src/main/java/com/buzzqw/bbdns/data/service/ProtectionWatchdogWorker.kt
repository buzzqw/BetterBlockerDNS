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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic watchdog: if the user had protection enabled but the VPN service is
 * no longer running (killed by the system or crashed), start it again.
 */
@HiltWorker
class ProtectionWatchdogWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firewallManager: FirewallManager,
    private val preferencesUseCases: PreferencesUseCases
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                    if (!settings.protectionEnabled || ProtectionState.isRunning) {
                        return@fold
                    }
                    if (!VpnManager.permissionChecker(applicationContext)) {
                        Logger.warn("Protection watchdog: VPN permission missing, cannot restart")
                        return@fold
                    }
                    Logger.info("Protection watchdog: protection down, restarting it")
                    firewallManager.setFirewallMode(FirewallMode.VPN)
                    firewallManager.startFirewall()
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
            Result.success()
        } catch (e: Exception) {
            Logger.error("Protection watchdog error: ${e.message}", e)
            Result.retry()
        }
    }
}
