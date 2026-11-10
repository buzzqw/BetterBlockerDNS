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

import com.buzzqw.bbdns.service.firewall.utils.DnsFilterExclusions
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.domain.model.CustomDomain
import com.buzzqw.bbdns.domain.repository.CustomDomainRepository
import com.buzzqw.bbdns.domain.usecase.log.LogUseCases
import com.buzzqw.bbdns.service.firewall.model.FireWallModel
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DNSModel
import com.buzzqw.bbdns.service.firewall.handler.RuleHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

class CustomDomainRule @Inject constructor(
    private val customDomainRepository: CustomDomainRepository
) : FirewallRule {
    
    /**
     * Rules prepared once when the list changes. The regex of a custom domain
     * used to be compiled again for every DNS query.
     */
    private class DomainMatcher(val domain: CustomDomain) {
        private val suffix = ".${domain.domain}"
        private val regex: Regex? = if (domain.isRegex) {
            try {
                Regex(domain.domain, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                Logger.error("Invalid regex pattern in custom domain: ${domain.domain}", e)
                null
            }
        } else null

        fun matches(domainName: String): Boolean = if (domain.isRegex) {
            regex?.matches(domainName) == true
        } else {
            // Exact match or subdomain match
            domainName.equals(domain.domain, ignoreCase = true) ||
                domainName.endsWith(suffix, ignoreCase = true)
        }
    }

    @Volatile private var allowlistDomains: List<DomainMatcher> = emptyList()
    @Volatile private var blocklistDomains: List<DomainMatcher> = emptyList()
    private var isCustomDomainRulesEnabled = true
    private var ruleHandler: RuleHandler? = null

    init {
        observeCustomDomains()
    }

    private fun observeCustomDomains() {
        CoroutineScope(Dispatchers.IO).launch {
            // Observe allowlist domains
            customDomainRepository.getEnabledAllowlistDomains().collect { domains ->
                allowlistDomains = domains.map(::DomainMatcher)
                Logger.info("Updated allowlist domains: ${domains.size} domains")
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            // Observe blocklist domains
            customDomainRepository.getEnabledBlocklistDomains().collect { domains ->
                blocklistDomains = domains.map(::DomainMatcher)
                Logger.info("Updated blocklist domains: ${domains.size} domains")
            }
        }
    }

    fun enableCustomDomainRules() {
        isCustomDomainRulesEnabled = true
        Logger.info("Custom domain rules enabled")
    }
    
    fun disableCustomDomainRules() {
        isCustomDomainRulesEnabled = false
        Logger.info("Custom domain rules disabled")
    }
    
    fun isCustomDomainRulesEnabled(): Boolean {
        return isCustomDomainRulesEnabled
    }
    
    fun setRuleHandler(handler: RuleHandler) {
        ruleHandler = handler
    }

    override fun check(
        packet: FireWallModel,
        dnsModel: DNSModel?,
        logUseCases: LogUseCases,
        result: FirewallResult
    ): FirewallResult {
        // If custom domain rules are disabled, don't affect the result
        if (!isCustomDomainRulesEnabled) {
            return result
        }
        
        // Apps excluded from DNS filtering skip the user's custom rules too.
        if (dnsModel != null && DnsFilterExclusions.isExcluded(packet.uid)) {
            return result
        }

        dnsModel?.let { dns ->
            val domainName = dns.domainName
            
            // Check blocklist first - if domain matches blocklist, block it
            for (blockDomain in blocklistDomains) {
                if (blockDomain.matches(domainName)) {
                    Logger.debug("Blocked custom domain: $domainName (matched: ${blockDomain.domain.domain})")
                    return FirewallResult.DNS_BLOCKED
                }
            }
            
            // Check allowlist - if domain matches allowlist, allow it (override other blocks)
            for (allowDomain in allowlistDomains) {
                if (allowDomain.matches(domainName)) {
                    Logger.debug("Allowed custom domain: $domainName (matched: ${allowDomain.domain.domain})")
                    return FirewallResult.ACCEPT
                }
            }
        }
        
        // No custom domain rules matched, return the current result unchanged
        return result
    }
}
