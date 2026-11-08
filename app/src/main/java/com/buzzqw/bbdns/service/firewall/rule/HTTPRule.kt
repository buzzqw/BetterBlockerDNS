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

class HTTPRule @Inject constructor(
    private val preferencesUseCases: PreferencesUseCases,
    private val externalScope: CoroutineScope
) : FirewallRule {

    private var blockHTTP = false
    private var allowLocal = false

    init {
        updateHTTPStatus()
    }

    fun updateHTTPStatus(enabled: Boolean? = null) {
        externalScope.launch(Dispatchers.IO) {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                    blockHTTP = settings.blockPort80
                    allowLocal = settings.allowLocal
                },
                ifFailure = { error ->
                    Logger.error("Failed to load settings: ${error.message}")
                }
            )
        }
    }

    override fun check(
        packet: FireWallModel,
        dnsModel: DNSModel?,
        logUseCases: LogUseCases,
        result: FirewallResult
    ): FirewallResult {
        return if (packet.destinationPort == 80 && blockHTTP) FirewallResult.DROP else FirewallResult.ACCEPT
    }
}