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

package com.buzzqw.bbdns.service.firewall.rule

import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.core.utils.extensions.resolveIpToHostname
import com.buzzqw.bbdns.domain.model.Log
import com.buzzqw.bbdns.domain.usecase.log.LogUseCases
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.service.firewall.model.FireWallModel
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DNSModel
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

class LogRule @Inject constructor(
    private val preferencesUseCases: PreferencesUseCases,
    private val externalScope: CoroutineScope
) : FirewallRule {

    private val mutex = Mutex()

    @Volatile
    private var isLogEnabled = false

    init {
        updateLogStatus()
    }

    fun updateLogStatus(enabled: Boolean? = null) {
        if (enabled != null) {
            isLogEnabled = enabled
        } else {
            externalScope.launch(Dispatchers.IO) {
                preferencesUseCases.loadSettings.execute().fold(
                    ifSuccess = { settings ->
                        mutex.withLock {
                            isLogEnabled = settings.logs
                        }
                    },
                    ifFailure = { error ->
                        Logger.error("Failed to load settings: ${error.message}")
                    }
                )
            }
        }
    }

    override fun check(
        packet: FireWallModel,
        dnsModel: DNSModel?,
        logUseCases: LogUseCases,
        result: FirewallResult
    ): FirewallResult {
        // DNS queries always carry the domain shown in the log screen, so they
        // are recorded every time. For all other traffic only the first packet
        // of a flow is logged (see FlowUidCache): writing a row for every
        // datagram filled the database and drained the battery.
        if (!isLogEnabled || (dnsModel == null && !packet.shouldLog)) {
            return FirewallResult.ACCEPT
        }

        externalScope.launch(Dispatchers.IO) {
            mutex.withLock {
                if (!isLogEnabled) return@withLock
                val log = Log(
                    packageID = packet.uid,
                    destinationIP = packet.destinationIP,
                    destinationPort = packet.destinationPort.toString(),
                    packetStatus = result,
                    sourceIP = packet.sourceIP,
                    sourcePort = packet.sourcePort.toString(),
                    protocol = when (packet.protocol) {
                        NetworkConstants.TCP_PROTOCOL -> "TCP"
                        NetworkConstants.UDP_PROTOCOL -> "UDP"
                        NetworkConstants.ICMP_PROTOCOL -> "ICMP"
                        else -> "UKW"
                    },
                    destinationAddress = dnsModel?.domainName ?: packet.destinationIP.resolveIpToHostname()
                )
                try {
                    logUseCases.addLog.execute(log)
                } catch (e: Exception) {
                    Logger.error("Failed to add log: ${e.message}")
                }
            }
        }
        return FirewallResult.ACCEPT
    }
}