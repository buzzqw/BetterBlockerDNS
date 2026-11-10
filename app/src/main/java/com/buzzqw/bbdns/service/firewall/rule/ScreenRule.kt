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
import com.buzzqw.bbdns.data.service.NetworkManager
import com.buzzqw.bbdns.data.service.ScreenManager
import com.buzzqw.bbdns.data.service.ScreenStateManager
import com.buzzqw.bbdns.domain.usecase.log.LogUseCases
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.service.firewall.model.FireWallModel
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DNSModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

class ScreenRule @Inject constructor(
    private val screenStateManager: ScreenStateManager,
    private val preferencesUseCases: PreferencesUseCases
) : FirewallRule {
    var blockWhenScreenOffWifi: Boolean? = null
    var blockWhenScreenOffData: Boolean? = null

    init {
        updateRules()
    }

    fun updateRules() {
        runBlocking {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                    blockWhenScreenOffData = settings.blockCellularWhenScreenOff
                    blockWhenScreenOffWifi = settings.blockWifiWhenScreenOff
                    screenStateManager.blockWhenScreenOff =
                        settings.blockCellularWhenScreenOff || settings.blockWifiWhenScreenOff
                }
            )
        }
    }

    override fun check(packet: FireWallModel, dnsModel: DNSModel?, logUseCases: LogUseCases, result: FirewallResult): FirewallResult {
        return when {
            blockWhenScreenOffData == true && packet.networkType == NetworkManager.ConnectionType.MOBILE -> {
                if (screenStateManager.currentScreenStatus == ScreenManager.SCREEN_OFF) {
                    FirewallResult.DROP
                } else FirewallResult.ACCEPT
            }
            blockWhenScreenOffWifi == true && packet.networkType == NetworkManager.ConnectionType.WIFI -> {
                if (screenStateManager.currentScreenStatus == ScreenManager.SCREEN_OFF) {
                    FirewallResult.DROP
                } else FirewallResult.ACCEPT
            }
            else -> FirewallResult.ACCEPT
        }
    }
}