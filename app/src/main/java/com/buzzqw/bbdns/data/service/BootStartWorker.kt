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

package com.buzzqw.bbdns.data.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.service.utils.manager.FirewallManager
import com.buzzqw.bbdns.service.utils.manager.FirewallMode
import com.buzzqw.bbdns.service.utils.manager.NetworkSpeedManager
import com.buzzqw.bbdns.service.utils.manager.VpnManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class BootStartWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val firewallManager: FirewallManager,
    private val preferencesUseCases: PreferencesUseCases
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                    val afterUpdate = inputData.getBoolean(KEY_AFTER_UPDATE, false)
                    if (if (afterUpdate) settings.protectionEnabled else settings.startOnBoot) {
                        if (VpnManager.permissionChecker(context)) {
                            firewallManager.setFirewallMode(FirewallMode.VPN)
                            firewallManager.startFirewall()
                        } else {
                            Logger.error("VPN permission not granted")
                        }
                    }

                    // Start network speed monitor if enabled
                    if (settings.networkSpeedMonitor) {
                        NetworkSpeedManager.start(context)
                        Logger.info("Network speed monitor started on boot")
                    }

                    Result.success()
                },
                ifFailure = { error ->
                    Logger.error("Failed to load settings for boot start: ${error.message}")
                    Result.failure()
                }
            )
        } catch (e: Exception) {
            Logger.error("Error in boot start worker: ${e.message}")
            Result.failure()
        }
    }

    companion object {
        const val KEY_AFTER_UPDATE = "after_update"
    }
}
