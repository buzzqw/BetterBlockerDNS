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

package com.buzzqw.bbdns

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.data.cache.DomainCacheService
import com.buzzqw.bbdns.data.service.ProtectionWatchdogWorker
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.AutoUpdateManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    @Inject
    lateinit var domainCacheService: DomainCacheService

    @Inject
    lateinit var preferencesUseCases: PreferencesUseCases

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        App.applicationContext = this
        instance = this
        
        // Initialize auto-update on app startup
        initializeAutoUpdate()

        // Keep protection alive: periodically check the VPN service.
        scheduleProtectionWatchdog()
        
        // Don't initialize domains on app startup - they'll be loaded when needed
        // This significantly improves app startup time
        // Domains will be loaded when:
        // 1. VPN service starts
        // 2. User navigates to DNS settings
        // 3. User manually triggers a refresh
        // 4. Auto-update worker runs
    }
    
    private fun initializeAutoUpdate() {
        // Read the saved interval and enabled flag before scheduling: the old
        // code always scheduled a 15-minute job, even when the user had turned
        // automatic updates off or kept the default 24 hours.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                    AutoUpdateManager.initializeAutoUpdate(
                        this@App,
                        settings.autoUpdateEnabled,
                        settings.autoUpdateInterval
                    )
                },
                ifFailure = { error ->
                    Logger.error("Cannot schedule blocklist auto-update: ${error.message}")
                }
            )
        }
    }

    private fun scheduleProtectionWatchdog() {
        try {
            val request = PeriodicWorkRequestBuilder<ProtectionWatchdogWorker>(
                15, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "protection_watchdog",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Exception) {
            Logger.error("Cannot schedule protection watchdog: ${e.message}")
        }
    }

    companion object {
        lateinit var applicationContext: Context
        private lateinit var instance: App
    }
}

