/*
 * Copyright (C) 2024 Vexzure
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

package com.buzzqw.bbdns.service.vpn.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.domain.model.Application
import com.buzzqw.bbdns.service.utils.receiver.AppChangeCallback
import com.buzzqw.bbdns.service.utils.receiver.AppChangeReceiver
import com.buzzqw.bbdns.domain.usecase.application.ApplicationUseCases
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.service.firewall.handler.RuleHandler
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters
import com.buzzqw.bbdns.service.firewall.utils.FirewallStatus
import com.buzzqw.bbdns.service.utils.manager.FirewallManager
import com.buzzqw.bbdns.service.utils.manager.ProtectionPause
import com.buzzqw.bbdns.service.utils.manager.ProtectionState
import com.buzzqw.bbdns.service.utils.manager.FirewallService
import com.buzzqw.bbdns.service.utils.manager.VpnManager
import com.buzzqw.bbdns.service.utils.notifications.showInstallNotification
import com.buzzqw.bbdns.service.utils.notifications.showStartNotification
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

@AndroidEntryPoint
class VpnConnectionServer : Service(), CoroutineScope by MainScope(), AppChangeCallback, FirewallService {

    @Inject lateinit var firewallManager: FirewallManager
    @Inject lateinit var ruleManager: RuleHandler
    @Inject lateinit var applicationUseCases: ApplicationUseCases
    @Inject lateinit var preferencesUseCases: PreferencesUseCases
    @Inject @ApplicationContext lateinit var appContext: Context


    private var vpnInterface: ParcelFileDescriptor? = null
    private lateinit var tunnelManager: TunnelManager
    private var netGuardJob: Job? = null
    private var notificationJob: Job? = null

    private var installedApplications: List<Application>? = null
    private val appChangeReceiver = AppChangeReceiver(this)
    private var isStoppedBySelf = false
    private var isShuttingDown = false

    inner class VpnConnection : Binder() {
        val service: VpnConnectionServer
            get() = this@VpnConnectionServer
    }

    fun loadApplications() {
        runBlocking {
            applicationUseCases.getApplications.execute().fold(
                ifSuccess = { apps -> installedApplications = apps },
                ifFailure = { Logger.error("Failed to fetch applications: ${it.message}") }
            )
        }
    }

    override fun startService(context: Context) {
        VpnManager.start(context)
    }

    override fun stopService(context: Context) {
        VpnManager.stop(context)
    }

    override fun updateLogs(enabled: Boolean) {
        ruleManager.updateLogs(enabled)
    }

    override fun updateScreen(value: Boolean) {
        ruleManager.updateScreenSetting()
    }


    override suspend fun updateDomains(progressCallback: (suspend (Int) -> Unit)?) {
        ruleManager.updateBlocklist(progressCallback)
    }

    override fun updateHttpSettings() {
        ruleManager.updateHttpSettings()
    }
    
    override fun setDnsBlocking(enabled: Boolean) {
        // Turning blocking back on also ends a pending pause.
        if (enabled && ProtectionState.isPaused) {
            ProtectionPause.resume()
        } else {
            ruleManager.setDnsBlocking(enabled)
        }
    }

    override fun pauseProtection(minutes: Int) {
        val rules = ruleManager
        ProtectionPause.pause(appContext, minutes) { enabled -> rules.setDnsBlocking(enabled) }
        refreshNotification()
    }

    override fun resumeProtection() {
        ProtectionPause.resume()
        refreshNotification()
    }

    /** Re-post the foreground notification so it shows the pause state. */
    private fun refreshNotification() {
        if (!ProtectionState.isRunning) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                this@VpnConnectionServer.showStartNotification(preferencesUseCases, appContext)
            } catch (e: Exception) {
                Logger.error("Cannot refresh notification: ${e.message}", e)
            }
        }
    }
    
    override fun isDnsBlockingEnabled(): Boolean {
        return ruleManager.isDnsBlockingEnabled()
    }


    override fun updateRules(application: Application?) {
        Logger.info("Updating firewall rules${if (application != null) " for ${application.packageID}" else ""}")
        
        // Clear sessions directly if tunnel manager is initialized
        try {
            if (::tunnelManager.isInitialized) {
                Logger.info("Clearing sessions for rule update")
                tunnelManager.clearSessions()
            }
        } catch (e: Exception) {

        }
        
        runBlocking {
            loadApplications()
            showStartNotification(preferencesUseCases, appContext)
        }
        
        if (::ruleManager.isInitialized) {
            ruleManager.updateAppRules()
            Logger.info("Firewall rules updated successfully")
        } else {
            Logger.error("Attempted to update rules before ruleManager was initialized.")
        }
    }

    override fun onAppInstalled(packageName: String?) {

        packageName?.let { pkg ->
            CoroutineScope(Dispatchers.IO).launch {
                showInstallNotification(
                    pkg,
                    applicationUseCases,
                    preferencesUseCases,
                    firewallManager = firewallManager
                )
            }
        }
    }

    override fun onAppUninstalled(packageName: String?) {

    }

    override fun onBind(intent: Intent): IBinder = VpnConnection()

    @SuppressLint("MissingPermission")
    private fun toggleWifiAccess(packageName: String?) {
        toggleAccess(packageName) { app -> app.copy(internetAccess = !app.internetAccess) }
    }

    @SuppressLint("MissingPermission")
    private fun toggleCellularAccess(packageName: String?) {
        toggleAccess(packageName) { app -> app.copy(cellularAccess = !app.cellularAccess) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            NetworkConstants.ACTION_TOGGLE_CELLURAL -> toggleWifiAccess(intent.getStringExtra("packageName"))
            NetworkConstants.ACTION_TOGGLE_WIFI -> toggleCellularAccess(intent.getStringExtra("packageName"))
            NetworkConstants.ACTION_STOP_VPN -> stopVpn(userInitiated = true)
            ProtectionPause.ACTION_PAUSE, ProtectionPause.ACTION_RESUME -> {
                if (!ProtectionState.isRunning) {
                    // Only reachable from a stale notification: nothing to pause.
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (intent?.action == ProtectionPause.ACTION_PAUSE) {
                    pauseProtection(intent?.getIntExtra(ProtectionPause.EXTRA_MINUTES, 15) ?: 15)
                } else {
                    resumeProtection()
                }
            }
            NetworkConstants.ACTION_START_VPN -> {
                startVpn(intent)
                return START_STICKY
            }
            NetworkConstants.ACTION_CLEAR_SESSIONS -> {
                clearSessions()
                // For session clearing, we don't need to keep the service running
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    /**
     * Clears all active sessions. This is useful when firewall rules are updated
     * to ensure previously allowed sessions are terminated and new rules are applied.
     */
    fun clearSessions() {
        try {
            if (isShuttingDown) {
                Logger.warn("Cannot clear sessions: VPN is shutting down")
                return
            }
            
            if (::tunnelManager.isInitialized) {
                Logger.info("Clearing all active sessions due to network change")
                tunnelManager.clearSessions()
                Logger.info("All sessions cleared successfully")
            } else {
                Logger.warn("TunnelManager not initialized for session clearing")
            }
        } catch (e: Exception) {
            Logger.error("Error clearing sessions: ${e.message}", e)
        }
    }


    private fun toggleAccess(packageName: String?, update: (Application) -> Application) {
        packageName?.let { pkg ->
            CoroutineScope(Dispatchers.IO).launch {
                applicationUseCases.getApplication.execute(pkg).fold(
                    ifSuccess = { app ->
                        val updatedApp = update(app)
                        applicationUseCases.updateApplication.execute(updatedApp).fold(
                            ifSuccess = {
                                ruleManager.updateAppRules(updatedApp)
                                showInstallNotification(
                                    pkg,
                                    applicationUseCases,
                                    preferencesUseCases,
                                    firewallManager = firewallManager
                                )
                            },
                            ifFailure = { Logger.error("Failed to update application: ${it.message}") }
                        )
                    },
                    ifFailure = { Logger.error("Failed to fetch application: ${it.message}") }
                )
            }
        }
    }

    private fun startVpn(intent: Intent) {
        val vpnInterfaceFd = intent.getIntExtra("vpnInterfaceFd", -1)
        if (vpnInterfaceFd != -1) {
            // Start foreground service immediately to avoid timing issues
            FirewallCounters.init(appContext)
            FirewallCounters.reset()
            // A new VPN session always starts with blocking on.
            ProtectionPause.resume()
            runBlocking {
                loadApplications()
                showStartNotification(preferencesUseCases)
            }
            
            vpnInterface = ParcelFileDescriptor.adoptFd(vpnInterfaceFd)
            vpnInterface?.let {
                if (!initializeVpnComponents(it)) return
                ProtectionState.isRunning = true
                ProtectionState.sessionStartedAt = System.currentTimeMillis()
                persistProtectionEnabled(true)
                startVpnCoroutines()
                startNotificationUpdates()
                appChangeReceiver.register(this)
            } ?: run {
                Logger.error("Invalid VPN interface file descriptor received. Service is stopping.")
                stopSelf()
            }
        }
    }

    private fun initializeVpnComponents(vpnInterface: ParcelFileDescriptor): Boolean {
        // Get DNS servers and DoH settings
        val (dnsServerV4, dnsServerV6, dohUrl) = runBlocking {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = {
                    Triple(
                        it.dnsServer1,
                        it.dnsServer1v6,
                        if (it.dohEnabled && it.dohUrl.isNotBlank()) it.dohUrl else null
                    )
                },
                ifFailure = { Triple("9.9.9.9", "2620:fe::fe", null) }
            )
        }

        // Initialize TunnelManager with the injected RuleHandler and user's DNS settings
        tunnelManager = TunnelManager(ruleManager, dnsServerV4, dnsServerV6, dohUrl)
        Logger.info("Tunnel Manager DNS mode: ${if (dohUrl != null) "DoH ($dohUrl)" else "plain ($dnsServerV4)"}")
        
        if (!tunnelManager.initialize()) {
            Logger.error("Failed to initialize Tunnel Manager")
            stopSelf()
            return false
        }

        Logger.info("Tunnel Manager initialized successfully")
        return true
    }


    /** Refresh the foreground notification with live allowed/blocked counters. */
    private fun startNotificationUpdates() {
        notificationJob?.cancel()
        notificationJob = launch(Dispatchers.IO) {
            var lastSnapshot: Any? = null
            while (isActive) {
                delay(5_000)
                if (isShuttingDown || !ProtectionState.isRunning) break
                // Catch a pause that expired while the device was asleep.
                ProtectionPause.resumeIfExpired()
                // Only re-post when the counters or the pause changed: saves
                // battery and avoids re-alerting the user.
                val snapshot = FirewallCounters.snapshot() to ProtectionState.pausedUntil.value
                if (snapshot == lastSnapshot) continue
                lastSnapshot = snapshot
                this@VpnConnectionServer.showStartNotification(preferencesUseCases, appContext)
            }
        }
    }

    private fun startVpnCoroutines() {
        launch { if (::firewallManager.isInitialized) { firewallManager.update(FirewallStatus.ONLINE) } }
        launch(Dispatchers.IO) { ruleManager.updateAppRules() }
        
        // Start Tunnel Manager VPN processing
        val logLevel = 2 // ANDROID_LOG_WARN
        tunnelManager.start(logLevel)
        
        netGuardJob = launch(Dispatchers.IO) {
            try {
                vpnInterface?.let { vpnFd ->
                    Logger.info("Starting Tunnel Manager packet processing")
                    tunnelManager.run(vpnFd.fd, forwardDns = true, rcode = 3)
                }
            } catch (e: Exception) {
                Logger.error("Tunnel Manager processing error: ${e.message}", e)
            }
        }
    }


    private fun closeResources() {
        isShuttingDown = true
        ProtectionState.isRunning = false
        ProtectionState.sessionStartedAt = 0L
        FirewallCounters.flush()
        notificationJob?.cancel()
        netGuardJob?.cancel()
        if (::tunnelManager.isInitialized) {
            tunnelManager.stop()
            tunnelManager.release()
        }
        vpnInterface?.close()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Immediately stop foreground to prevent timeout exception
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }

        // Do cleanup in background to avoid blocking main thread
        if (!isStoppedBySelf) {
            val userInitiated = ProtectionState.userStopRequested
            ProtectionState.userStopRequested = false
            Thread {
                try {
                    stopVpn(userInitiated)
                } catch (e: Exception) {
                    Logger.error("Error during service cleanup: ${e.message}", e)
                }
            }.start()
        }
    }


    /** Remember whether protection should be running, for the watchdog. */
    private fun persistProtectionEnabled(enabled: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                    if (settings.protectionEnabled != enabled) {
                        preferencesUseCases.saveSettings.execute(
                            settings.copy(protectionEnabled = enabled)
                        )
                    }
                },
                ifFailure = { Logger.error("Cannot persist protection state: ${it.message}") }
            )
        }
    }

    private fun stopVpn(userInitiated: Boolean = false) {
        appChangeReceiver.unregister(this)
        ProtectionState.isRunning = false
        ProtectionState.sessionStartedAt = 0L
        ProtectionPause.resume()
        FirewallCounters.flush()
        notificationJob?.cancel()
        if (userInitiated) {
            // The user turned protection off: the watchdog must not restart it.
            persistProtectionEnabled(false)
        }
        if (::firewallManager.isInitialized) {
            if (firewallManager.rulesLoaded.value.name() == FirewallStatus.ONLINE.name()) {
                firewallManager.update(FirewallStatus.OFFLINE)
                coroutineContext.cancel()
                try {
                    closeResources()
                } catch (e: IOException) {
                    Logger.error("Error closing resources: ${e.localizedMessage}", e)
                }

                // stopForeground is now called in onDestroy() to prevent timeout
                Logger.info("Vpn server is stopped")
                isStoppedBySelf = true
                stopSelf()
            }
        } else {
            Logger.warn("Vpn server is not running")
        }
    }
}


