/*
 * Copyright (C) 2026 buzzqw
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
import com.buzzqw.bbdns.service.firewall.model.FireWallModel
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import com.buzzqw.bbdns.service.firewall.utils.DnsFilterExclusions
import com.buzzqw.bbdns.service.firewall.utils.DnsProtectionState
import com.buzzqw.bbdns.service.firewall.utils.DohBypass
import com.buzzqw.bbdns.service.vpn.network.transport.dns.DNSModel
import javax.inject.Inject

/**
 * Keeps DNS inside the filter when an app tries to bypass it.
 *
 * Three leaks are covered, all behind the *Block DNS bypass* setting:
 * - DoH bootstrap hostnames and the Firefox canary domain, answered as
 *   blocked so the client falls back to the system resolver;
 * - hardcoded public DoH resolvers on 443, which never ask DNS;
 * - DNS-over-TLS on 853, used by Android Private DNS.
 *
 * Per-app DNS exclusions are respected: an app that the user excluded from
 * filtering keeps its own DNS path.
 */
class DohBypassRule @Inject constructor() : FirewallRule {

    override fun check(
        packet: FireWallModel,
        dnsModel: DNSModel?,
        logUseCases: LogUseCases,
        result: FirewallResult
    ): FirewallResult {
        if (!DnsProtectionState.isDohBypassActive || result != FirewallResult.ACCEPT) return result
        if (packet.uid > 0 && DnsFilterExclusions.isExcluded(packet.uid)) return result

        if (dnsModel != null) {
            return if (DohBypass.matches(dnsModel.domainName)) {
                FirewallResult.DNS_BLOCKED
            } else {
                result
            }
        }

        return if (DohBypass.blocksTransport(
                packet.protocol.toInt(),
                packet.destinationPort,
                packet.destinationIP
            )
        ) {
            FirewallResult.DROP
        } else {
            result
        }
    }
}
