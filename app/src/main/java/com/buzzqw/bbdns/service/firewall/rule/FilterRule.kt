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

import com.buzzqw.bbdns.domain.model.Ip
import com.buzzqw.bbdns.domain.usecase.log.LogUseCases
import com.buzzqw.bbdns.domain.usecase.networkFilter.NetworkFilterUseCases
import com.buzzqw.bbdns.service.firewall.model.FireWallModel
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DNSModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class FilterRule @Inject constructor(
    private val networkFilterUseCases: NetworkFilterUseCases
) : FirewallRule {
    init {
        observeIps()
    }

    private var ips: List<Ip>? = null

    fun observeIps() {
        CoroutineScope(Dispatchers.IO).launch {
            val packagesList = networkFilterUseCases.getIps.execute()
            packagesList.fold(
                ifSuccess = {
                    it.collect { ip ->
                        ips = ip
                    }
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
        val isBlocked = ips?.firstOrNull { it.ip == packet.destinationIP } == null

        return if (!isBlocked) FirewallResult.DROP else FirewallResult.ACCEPT

    }
}
