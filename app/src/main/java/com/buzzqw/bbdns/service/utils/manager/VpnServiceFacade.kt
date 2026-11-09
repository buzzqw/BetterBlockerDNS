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

package com.buzzqw.bbdns.service.utils.manager

import android.content.Context
import com.buzzqw.bbdns.domain.model.Application
import com.buzzqw.bbdns.service.firewall.handler.RuleHandler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback used before Android delivers the bound VPN service.
 *
 * Creating a Service instance by hand bypasses its lifecycle and leaves the
 * Hilt-injected fields empty, so this adapter only forwards the calls that do
 * not need the tunnel. Once [FirewallManager] is bound, every call goes to the
 * real service.
 */
@Singleton
class VpnServiceFacade @Inject constructor(
    private val ruleHandler: RuleHandler,
    @ApplicationContext private val context: Context,
) : FirewallService {

    override fun updateRules(application: Application?) {
        ruleHandler.updateAppRules(application)
    }

    override fun startService(context: Context) {
        VpnManager.start(context)
    }

    override fun stopService(context: Context) {
        VpnManager.stop(context)
    }

    override fun updateLogs(enabled: Boolean) {
        ruleHandler.updateLogs(enabled)
    }

    override fun updateScreen(value: Boolean) {
        ruleHandler.updateScreenSetting()
    }

    override suspend fun updateDomains(progressCallback: (suspend (Int) -> Unit)?) {
        ruleHandler.updateBlocklist(progressCallback)
    }

    override fun updateHttpSettings() {
        ruleHandler.updateHttpSettings()
    }

    override fun setDnsBlocking(enabled: Boolean) {
        if (enabled && ProtectionState.isPaused) {
            ProtectionPause.resume()
        } else {
            ruleHandler.setDnsBlocking(enabled)
        }
    }

    override fun isDnsBlockingEnabled(): Boolean = ruleHandler.isDnsBlockingEnabled()

    override fun pauseProtection(minutes: Int) {
        ProtectionPause.pause(context, minutes) { enabled ->
            ruleHandler.setDnsBlocking(enabled)
        }
    }

    override fun resumeProtection() {
        ProtectionPause.resume()
    }
}
