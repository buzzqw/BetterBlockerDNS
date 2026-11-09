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

package com.buzzqw.bbdns.di.firewall

import android.content.Context
import com.buzzqw.bbdns.service.firewall.handler.RuleHandler
import com.buzzqw.bbdns.service.utils.manager.FirewallManager
import com.buzzqw.bbdns.service.utils.manager.FirewallService
import com.buzzqw.bbdns.service.utils.manager.FirewallStateManager
import com.buzzqw.bbdns.service.utils.manager.VpnServiceFacade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object FirewallModule {
    @Provides
    @Singleton
    fun provideFirewallState(): FirewallStateManager {
        return FirewallStateManager()
    }

    @Provides
    @Singleton
    fun provideVpnServiceFacade(
        ruleHandler: RuleHandler,
        @ApplicationContext context: Context,
    ): FirewallService {
        return VpnServiceFacade(ruleHandler, context)
    }

    @Provides
    @Singleton
    fun provideFirewallManager(
        firewallStateManager: FirewallStateManager,
        @ApplicationContext context: Context,
        vpnService: FirewallService
    ): FirewallManager {
        return FirewallManager(firewallStateManager, context, vpnService)
    }
}
