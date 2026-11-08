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

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.domain.model.Settings
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.service.utils.manager.ProtectionState
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject

@AndroidEntryPoint
class VpnConnectionClient : VpnService() {

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var preferencesUseCases: PreferencesUseCases
    @Inject lateinit var applicationUseCases: com.buzzqw.bbdns.domain.usecase.application.ApplicationUseCases
    @Inject lateinit var domainCacheService: com.buzzqw.bbdns.data.cache.DomainCacheService


    private var settings: Settings? = null
    private var isStoppedBySelf = false
    private var vpnInterface: ParcelFileDescriptor? = null
    private var fd: Int = -1


    fun loadSettings() {
        runBlocking {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings = it },
                ifFailure = { Logger.error("Failed to fetch applications: ${it.message}") }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            // Sticky restart after the process was killed: resume only if the
            // user left protection on.
            loadSettings()
            if (settings?.protectionEnabled == true) {
                Logger.info("VPN client restarted by the system, resuming protection")
                startVpnIfNotRunning()
            }
        } else {
            handleAction(action)
        }
        return START_STICKY
    }

    private fun handleAction(action: String) {
        when (action) {
            NetworkConstants.ACTION_START_VPN -> startVpnIfNotRunning()
            // Started by Android "Always-on VPN" (boot, network change, settings).
            VpnService.SERVICE_INTERFACE -> {
                Logger.info("VPN started by the system (Always-on VPN)")
                startVpnIfNotRunning()
            }
            NetworkConstants.ACTION_STOP_VPN -> stopVpn()
            else -> Logger.warn("Unhandled action: $action")
        }
    }

    private fun startVpnIfNotRunning() {
        if (vpnInterface == null) {
            try {
                // Initialize domain cache when VPN starts (lazy loading)
                domainCacheService.initializeGlobally()

                configureAndStartVpn()
            } catch (e: Exception) {
                Logger.error("Error starting VPN", e)
                onDestroy()
            }
        } else {
            Logger.info("VPN is already running.")
        }
    }

    private fun configureAndStartVpn() {
        vpnInterface = createVpnBuilder().establish()
        ProtectionState.revokedByOtherVpn = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ProtectionState.alwaysOn = isAlwaysOn
        }
        vpnInterface?.let {
            fd = it.detachFd()
            startVpnServiceWithFd(fd)
        } ?: run {
            Logger.error("Failed to establish VPN interface.")
            onDestroy()
        }
    }

    private fun createVpnBuilder() = Builder().apply {
        loadSettings()
        addDnsServer(NetworkConstants.VPN_DNS_ADDRESS)    // IPv4 DNS
        // The secondary resolver is reachable through the same tunnel address
        // family: Android falls back to it when the primary does not answer.
        if (!settings!!.dnsServer2.isBlank()) {
            addDnsServer(NetworkConstants.VPN_DNS_ADDRESS_ALT)
        }
        addAddress(settings!!.ipv4, 32)
        // IPv6 disabled - filtering not implemented, would allow unfiltered traffic
        // addAddress(settings!!.ipv6, 128)
        addRoute(NetworkConstants.ALL_TRAFFIC, 0)  // Route IPv4 traffic only
        // addRoute("::", 0)  // IPv6 routing disabled
        setMtu(NetworkConstants.MAX_PACKET_LEN)
        setBlocking(true)
        setSession(getString(R.string.app_name))
        addDisallowedApplication(context.packageName)
        addDisallowedApplication("com.google.android.projection.gearhead")

        // Add VPN bypass for apps that have bypassVpn=true
        runBlocking {
            applicationUseCases.getApplications.execute().fold(
                ifSuccess = { apps: List<com.buzzqw.bbdns.domain.model.Application> ->
                    apps.filter { app: com.buzzqw.bbdns.domain.model.Application -> app.bypassVpn }
                        .forEach { app: com.buzzqw.bbdns.domain.model.Application ->
                            try {
                                addDisallowedApplication(app.packageID)
                                Logger.info("VPN: Bypassing VPN for ${app.packageID}")
                            } catch (e: Exception) {
                                Logger.error("VPN: Failed to bypass VPN for ${app.packageID}: ${e.message}")
                            }
                        }
                },
                ifFailure = { error: com.buzzqw.bbdns.core.utils.Error ->
                    Logger.error("VPN: Failed to load apps for VPN bypass: ${error.message}")
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            setMetered(false)
        }
    }

    private fun startVpnServiceWithFd(fd: Int) {
        val intent = createServiceIntent(fd, NetworkConstants.ACTION_START_VPN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            startService(intent)
        }
    }

    private fun stopVpn(shouldStopSelf: Boolean = true) {
        stopVpnService(fd)
        vpnInterface.closeSafely()
        vpnInterface = null
        if (shouldStopSelf) {
            isStoppedBySelf = true
            stopSelf()
        } else {
            Logger.info("Firewall was stopped, possibly by another VPN.")
        }
    }

    private fun stopVpnService(fd: Int) {
        try {
            val intent = createServiceIntent(fd, NetworkConstants.ACTION_STOP_VPN)
            stopService(intent)
        } catch (e: Exception) {
            Logger.error("Failed to stop VPN service: ${e.message}", e)
        }
    }

    private fun createServiceIntent(fd: Int, action: String) = Intent(this, VpnConnectionServer::class.java).apply {
        this.action = action
        putExtra("vpnInterfaceFd", fd)
    }

    private fun ParcelFileDescriptor?.closeSafely() {
        try {
            this?.close()
        } catch (e: IOException) {
            Logger.error("Failed to close VPN interface: ${e.localizedMessage}", e)
        }
    }

    /** Another VPN app took over: Android allows only one VPN at a time. */
    override fun onRevoke() {
        Logger.warn("VPN permission revoked, probably by another VPN app")
        ProtectionState.revokedByOtherVpn = true
        // Treat it like a user stop, so the watchdog does not fight the other VPN.
        ProtectionState.userStopRequested = true
        super.onRevoke()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isStoppedBySelf) {
            stopVpn(false)
        }
    }
}
