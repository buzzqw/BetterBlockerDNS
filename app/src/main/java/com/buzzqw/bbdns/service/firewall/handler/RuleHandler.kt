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

package com.buzzqw.bbdns.service.firewall.handler

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase
import com.buzzqw.bbdns.data.service.NetworkChangeReceiver
import com.buzzqw.bbdns.data.service.ScreenStateManager
import com.buzzqw.bbdns.data.service.ScreenStateReceiver
import com.buzzqw.bbdns.domain.model.Application
import com.buzzqw.bbdns.domain.usecase.log.LogUseCases
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.service.firewall.model.FireWallModel
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import com.buzzqw.bbdns.service.firewall.rule.AppRule
import com.buzzqw.bbdns.service.firewall.rule.CustomDomainRule
import com.buzzqw.bbdns.service.firewall.rule.DNSRule
import com.buzzqw.bbdns.service.firewall.rule.FilterRule
import com.buzzqw.bbdns.service.firewall.rule.FirewallRule
import com.buzzqw.bbdns.service.firewall.rule.HTTPRule
import com.buzzqw.bbdns.service.firewall.rule.LogRule
import com.buzzqw.bbdns.service.firewall.rule.ScreenRule
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters
import com.buzzqw.bbdns.service.firewall.utils.TemporaryDnsAllow
import com.buzzqw.bbdns.service.firewall.utils.isLocalIPv4
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DNSModel
import com.buzzqw.bbdns.service.vpn.network.transport.ipv4.IPv4
import com.buzzqw.bbdns.service.vpn.network.transport.udp.UDPModel
import com.buzzqw.bbdns.service.vpn.service.VpnConnectionServer
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Serializable
import javax.inject.Inject
import org.xbill.DNS.DClass
import org.xbill.DNS.Type

class RuleHandler @Inject constructor(
    private val rules: List<FirewallRule>,
    private val logUseCases: LogUseCases,
    private val preferencesUseCases: PreferencesUseCases,
    private val ruleDatabase: RuleDatabase,
    networkChangeReceiver: NetworkChangeReceiver,
    @ApplicationContext private val context: Context
) {
    var allowLocal = false

    /**
     * The rules are split once: building a sublist for every packet created
     * garbage on the traffic path.
     */
    private val ruleChain = rules.dropLast(1)
    private val logRule = rules.last()

    init {
        val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        val screenStateReceiver = ScreenStateReceiver()
        val intentFilter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }

        registerReceiver(context, networkChangeReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        registerReceiver(context, screenStateReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

        CoroutineScope(Dispatchers.IO).launch {
            preferencesUseCases.loadSettings.execute().fold(
                ifSuccess = { settings ->
                   allowLocal = settings.allowLocal
                }
            )
        }
        
        rules.filterIsInstance<CustomDomainRule>().forEach { customDomainRule ->
            customDomainRule.setRuleHandler(this)
        }
    }

    suspend fun updateBlocklist(progressCallback: (suspend (Int) -> Unit)? = null) {
        rules.filterIsInstance<DNSRule>().forEach {
            it.updateBlocklist(progressCallback)
        }
    }

    fun updateLogs(enabled: Boolean) {
        rules.filterIsInstance<LogRule>().forEach {
            it.updateLogStatus(enabled)
        }
    }
    
    fun setDnsBlocking(enabled: Boolean) {
        rules.filterIsInstance<DNSRule>().forEach { dnsRule ->
            if (enabled) {
                dnsRule.enableDnsBlocking()
            } else {
                dnsRule.disableDnsBlocking()
            }
        }
    }
    
    fun isDnsBlockingEnabled(): Boolean {
        return rules.filterIsInstance<DNSRule>().firstOrNull()?.isDnsBlockingEnabled() ?: false
    }

    fun setCustomDomainRules(enabled: Boolean) {
        rules.filterIsInstance<CustomDomainRule>().forEach { customDomainRule ->
            if (enabled) {
                customDomainRule.enableCustomDomainRules()
            } else {
                customDomainRule.disableCustomDomainRules()
            }
        }
    }
    
    fun isCustomDomainRulesEnabled(): Boolean {
        return rules.filterIsInstance<CustomDomainRule>().firstOrNull()
            ?.isCustomDomainRulesEnabled() ?: false
    }

    /**
     * True when [host] is blocked right now, with the same precedence as the
     * packet path: an explicit allow wins, then the user's custom rules, then
     * the lists. A "continue" from the notice page counts as an allow.
     *
     * This is the hostname-only view used by the local notice page and the
     * CNAME check; it has no app (UID) information.
     */
    fun isDomainBlocked(host: String): Boolean {
        val domain = host.trim().trimEnd('.').lowercase()
        if (domain.isEmpty()) return false
        if (!isDnsBlockingEnabled()) return false
        if (TemporaryDnsAllow.isAllowed(domain)) return false

        val custom = rules.filterIsInstance<CustomDomainRule>().firstOrNull()
        if (custom?.isBlockedByUserRule(domain) == true) return true
        if (custom?.isAllowlisted(domain) == true) return false

        if (ruleDatabase.isAllowlisted(domain)) return false
        return ruleDatabase.isBlocked(domain)
    }

    /** True when an allow rule (list whitelist or custom allow) covers [host]. */
    fun isDomainExplicitlyAllowed(host: String): Boolean {
        val domain = host.trim().trimEnd('.').lowercase()
        if (domain.isEmpty()) return false
        val custom = rules.filterIsInstance<CustomDomainRule>().firstOrNull()
        if (custom?.isAllowlisted(domain) == true) return true
        if (TemporaryDnsAllow.isAllowed(domain)) return true
        return ruleDatabase.isAllowlisted(domain)
    }

    /** Title of the source that blocks [host], when the index knows it. */
    fun blockSourceOf(host: String): String? {
        val domain = host.trim().trimEnd('.').lowercase()
        if (domain.isEmpty()) return null
        val custom = rules.filterIsInstance<CustomDomainRule>().firstOrNull()
        if (custom?.isBlockedByUserRule(domain) == true) return "Custom domain rules"
        return ruleDatabase.sourceFor(domain)?.title
    }

    /** The user chose "continue" on the notice page. */
    fun allowTemporarily(host: String, minutes: Int = NOTICE_ALLOW_MINUTES) {
        TemporaryDnsAllow.allow(host, minutes)
    }

    /** True while a "continue" from the notice page is still valid. */
    fun isTemporarilyAllowed(host: String): Boolean = TemporaryDnsAllow.isAllowed(host)

    /**
     * Journals a block decided after the answer arrived: the query itself was
     * allowed, so [handle] never saw it and the log would not explain why the
     * app could not reach the site.
     */
    fun recordCnameBlock(
        uid: Int,
        udpHeader: UDPModel,
        ipHeader: IPv4?,
        domain: String,
        target: String
    ) {
        val model = ProtocolHandlerFactory.getHandler(udpHeader)?.handle(udpHeader, ipHeader)
            ?: FireWallModel()
        model.uid = uid
        val dnsModel = DNSModel(0, 0, 1, 0, 0, 0, domain, Type.A, DClass.IN)
        logRule.check(model, dnsModel, logUseCases, FirewallResult.DNS_BLOCKED)
        FirewallCounters.record(false, domain, uid)
        Logger.info("Blocked $domain: it resolves through $target (CNAME cloaking)")
    }

    fun updateScreenSetting() {
        rules.filterIsInstance<ScreenRule>().forEach {
            it.updateRules()
        }
    }

    fun updateHttpSettings() {
        rules.filterIsInstance<HTTPRule>().forEach {
            it.updateHTTPStatus()
        }
    }

    fun updateAppRules(updatedApplication: Application? = null) {
        rules.filterIsInstance<AppRule>().forEach {
            it.observePackages(updatedApplication)
        }
        rules.filterIsInstance<FilterRule>().forEach {
            it.observeIps()
        }
    }

    fun handle(packet: FireWallModel, dnsModel: DNSModel? = null, bypassCheck: Boolean): Triple<Boolean, Int, FirewallResult> {
        if (bypassCheck) {
            return Triple(true, packet.uid, FirewallResult.ACCEPT)
        }

        if (allowLocal && dnsModel == null) {
            if (isLocalIPv4(packet.destinationIP)) {
                return Triple(true, packet.uid, FirewallResult.ACCEPT)
            }
        }

        var result = FirewallResult.ACCEPT
        packet.apply {
            for (rule in ruleChain) {
                val ruleResult = rule.check(packet, dnsModel, logUseCases, result)
                if (result == FirewallResult.ACCEPT && ruleResult != FirewallResult.ACCEPT) {
                    result = ruleResult
                }
            }
        }

        logRule.check(packet, dnsModel, logUseCases, result)
        // Count DNS queries, plus connections blocked by the per-app rules:
        // counting every allowed packet would make the block rate meaningless.
        if (dnsModel != null || result != FirewallResult.ACCEPT) {
            FirewallCounters.record(result == FirewallResult.ACCEPT, dnsModel?.domainName, packet.uid)
        }
        return Triple(result == FirewallResult.ACCEPT, packet.uid, result)
    }

    companion object {
        /** How long "continue" on the notice page lasts. */
        const val NOTICE_ALLOW_MINUTES = 10
    }
}
