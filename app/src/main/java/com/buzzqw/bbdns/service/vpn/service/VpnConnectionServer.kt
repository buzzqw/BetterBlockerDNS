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
import com.buzzqw.bbdns.domain.model.Settings
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
import com.buzzqw.bbdns.service.widget.BbdsWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/** How often the foreground counters are refreshed (was 5 s). */
private const val NOTIFICATION_REFRESH_MS = 15_000L
/** How often the notification settings are re-read. */
private const val SETTINGS_REFRESH_MS = 5 * 60_000L

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
            NetworkConstants.ACTION_TOGGLE_CELLURAL -> toggleCellularAccess(intent.getStringExtra("packageName"))
            NetworkConstants.ACTION_TOGGLE_WIFI -> toggleWifiAccess(intent.getStringExtra("packageName"))
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
                if (!ProtectionState.isRunning) {
                    // A stale request must not keep an idle service alive.
                    stopSelf()
                    return START_NOT_STICKY
                }
                // A network change only clears the sessions: the VPN keeps
                // running and must stay sticky, otherwise Android would not
                // restart it after a process kill.
                clearSessions()
                return START_STICKY
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
            // A new session starts with blocking on. A pause saved before the
            // process was killed is restored below, once the rules are ready.
            ProtectionPause.resetSession()
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
                // Honour a pause that was running when the service was killed.
                ProtectionPause.restore(appContext) { enabled ->
                    ruleManager.setDnsBlocking(enabled)
                }
                BbdsWidgetProvider.updateAll(appContext)
            } ?: run {
                Logger.error("Invalid VPN interface file descriptor received. Service is stopping.")
                stopSelf()
            }
        }
    }

    private fun initializeVpnComponents(vpnInterface: ParcelFileDescriptor): Boolean {
        // Get DNS servers and DoH settings
        var dnsServerV4 = "9.9.9.9"
        var dnsServerV4Alt = "149.112.112.112"
        var dnsServerV6 = "2620:fe::fe"
        var dohUrl: String? = null
        runBlocking {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = {
                    dnsServerV4 = it.dnsServer1
                    dnsServerV4Alt = it.dnsServer2
                    dnsServerV6 = it.dnsServer1v6
                    dohUrl = if (it.dohEnabled && it.dohUrl.isNotBlank()) it.dohUrl else null
                },
                ifFailure = { Logger.error("Cannot read DNS settings: ${it.message}") }
            )
        }

        // Initialize TunnelManager with the injected RuleHandler and user's DNS settings
        tunnelManager = TunnelManager(
            ruleManager,
            dnsServerV4,
            dnsServerV6,
            dnsServerV4Alt,
            dohUrl,
        )
        Logger.info("Tunnel Manager DNS mode: ${if (dohUrl != null) "DoH ($dohUrl)" else "plain ($dnsServerV4 / $dnsServerV4Alt)"}")
        
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
            var lastSnapshot: Any? = FirewallCounters.snapshot() to ProtectionState.pausedUntil.value
            var cachedSettings: Settings? = null
            var settingsLoadedAt = 0L
            while (isActive) {
                delay(NOTIFICATION_REFRESH_MS)
                if (isShuttingDown || !ProtectionState.isRunning) break
                // Catch a pause that expired while the device was asleep.
                ProtectionPause.resumeIfExpired()
                // Only re-post when the counters or the pause changed: saves
                // battery and avoids re-alerting the user.
                val snapshot = FirewallCounters.snapshot() to ProtectionState.pausedUntil.value
                if (snapshot == lastSnapshot) continue
                lastSnapshot = snapshot

                // Re-read the settings only every few minutes instead of on
                // every refresh: the notification is rebuilt often enough.
                val now = System.currentTimeMillis()
                if (cachedSettings == null || now - settingsLoadedAt >= SETTINGS_REFRESH_MS) {
                    cachedSettings = preferencesUseCases.loadSettings.execute().fold(
                        ifSuccess = { it },
                        ifFailure = { error ->
                            Logger.error("Cannot refresh notification settings: ${error.message}")
                            cachedSettings
                        }
                    )
                    settingsLoadedAt = now
                }
                this@VpnConnectionServer.showStartNotification(
                    preferencesUseCases,
                    appContext,
                    settings = cachedSettings
                )
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
        // Turning protection off ends the pause; a system stop keeps it, so a
        // watchdog restart can restore the remaining time.
        if (userInitiated) {
            ProtectionPause.clear(appContext)
        } else {
            ProtectionPause.resetSession()
        }
        BbdsWidgetProvider.updateAll(appContext)
        FirewallCounters.flush()
        notificationJob?.cancel()
        if (userInitiated) {
            // The user turned protection off: the watchdog must not restart it.
            persistProtectionEnabled(false)
        }
        if (::firewallManager.isInitialized) {
            firewallManager.update(FirewallStatus.OFFLINE)
        } else {
            Logger.warn("Vpn server is not running")
        }
        // Close the TUN descriptor and the tunnel even when the status was not
        // ONLINE: otherwise a failed or interrupted start would leak them.
        try {
            closeResources()
        } catch (e: Exception) {
            Logger.error("Error closing resources: ${e.localizedMessage}", e)
        }
        coroutineContext.cancel()

        // stopForeground is now called in onDestroy() to prevent timeout
        Logger.info("Vpn server is stopped")
        isStoppedBySelf = true
        stopSelf()
    }
}


