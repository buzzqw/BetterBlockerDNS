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

import com.buzzqw.bbdns.domain.usecase.log.LogUseCases
import com.buzzqw.bbdns.domain.usecase.networkFilter.NetworkFilterUseCases
import com.buzzqw.bbdns.service.firewall.model.FireWallModel
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import com.buzzqw.bbdns.service.firewall.utils.SessionInvalidator
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DNSModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

class FilterRule @Inject constructor(
    private val networkFilterUseCases: NetworkFilterUseCases
) : FirewallRule {
    init {
        observeIps()
    }

    /** Blocked destination IPs, as a set: a list scan per packet was O(n). */
    @Volatile
    private var blockedIps: Set<String> = emptySet()

    /**
     * The IP flow never completes: every call used to start one more
     * collector that stayed alive for the whole process.
     */
    private var observeJob: Job? = null

    @Synchronized
    fun observeIps() {
        observeJob?.cancel()
        observeJob = CoroutineScope(Dispatchers.IO).launch {
            val packagesList = networkFilterUseCases.getIps.execute()
            packagesList.fold(
                ifSuccess = {
                    it.collect { ip ->
                        val updated = ip.mapTo(HashSet(ip.size * 2)) { entry -> entry.ip }
                        if (updated != blockedIps) {
                            blockedIps = updated
                            // Open flows were accepted under the old list.
                            SessionInvalidator.invalidate()
                        }
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
        return if (packet.destinationIP in blockedIps) FirewallResult.DROP else FirewallResult.ACCEPT
    }
}
