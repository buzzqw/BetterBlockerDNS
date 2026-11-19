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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.buzzqw.bbdns.core.logging.Logger
import java.util.concurrent.TimeUnit

/**
 * Scheduling of [ProtectionWatchdogWorker].
 *
 * The watchdog only exists to restart a protection that the system killed, so
 * it is queued while protection is on and cancelled when the user turns it
 * off: a periodic job that wakes the process every 15 minutes to find nothing
 * to do is pure battery cost.
 */
object ProtectionWatchdog {
    private const val WORK_NAME = "protection_watchdog"
    private const val INTERVAL_MINUTES = 15L

    /** Enqueue the periodic check; harmless when it is already queued. */
    fun schedule(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<ProtectionWatchdogWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            Logger.error("Cannot schedule protection watchdog: ${e.message}")
        }
    }

    /** Stop the periodic check: protection is off and must stay off. */
    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        } catch (e: Exception) {
            Logger.error("Cannot cancel protection watchdog: ${e.message}")
        }
    }
}
