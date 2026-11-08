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
import com.buzzqw.bbdns.domain.usecase.log.LogUseCases
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase
import com.buzzqw.bbdns.service.firewall.model.FireWallModel
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import com.buzzqw.bbdns.service.firewall.utils.DnsFilterExclusions
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DNSModel
import javax.inject.Inject

class DNSRule @Inject constructor(
    private val ruleDatabase: RuleDatabase
) : FirewallRule {
    private var isDnsBlockingEnabled = true

    suspend fun updateBlocklist(progressCallback: (suspend (Int) -> Unit)? = null) {
        ruleDatabase.initialize(progressCallback = progressCallback)
    }
    
    fun enableDnsBlocking() {
        isDnsBlockingEnabled = true
        Logger.info("DNS blocking enabled")
    }
    
    fun disableDnsBlocking() {
        isDnsBlockingEnabled = false
        Logger.info("DNS blocking disabled")
    }
    
    fun isDnsBlockingEnabled(): Boolean {
        return isDnsBlockingEnabled
    }

    override fun check(
        packet: FireWallModel,
        dnsModel: DNSModel?,
        logUseCases: LogUseCases,
        result: FirewallResult
    ): FirewallResult {
        // If DNS blocking is disabled, always allow
        if (!isDnsBlockingEnabled) {
            return result
        }
        
        dnsModel?.let {
            // Apps excluded from DNS filtering: the query is accepted (and still
            // logged and counted as allowed). packet.uid is resolved by AppRule,
            // which runs before this rule; see DnsFilterExclusions for limits.
            if (DnsFilterExclusions.isExcluded(packet.uid)) {
                return FirewallResult.ACCEPT
            }
            if (ruleDatabase.isBlocked(dnsModel.domainName)) {
                Logger.info("Blocked ${dnsModel.domainName}")
                return FirewallResult.DNS_BLOCKED
            } else {
                return FirewallResult.ACCEPT

            }
        } ?: return FirewallResult.ACCEPT
    }
}