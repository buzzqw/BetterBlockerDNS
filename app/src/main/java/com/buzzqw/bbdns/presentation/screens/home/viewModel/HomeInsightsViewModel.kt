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

package com.buzzqw.bbdns.presentation.screens.home.viewModel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.data.cache.DomainCacheService
import com.buzzqw.bbdns.presentation.config
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostException
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase.Companion.ListStat
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Actions offered by the home dashboard on the blocked domains it shows. */
@HiltViewModel
class HomeInsightsViewModel @Inject constructor(
    private val domainCacheService: DomainCacheService,
    private val ruleDatabase: RuleDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    /** Per-list entries of the rules currently loaded, for the active lists card. */
    val listStats: StateFlow<List<ListStat>> = RuleDatabase.listStats

    private val _history = MutableStateFlow<List<FirewallCounters.DayStats>>(emptyList())
    /** Daily allowed/blocked totals of the last 30 days, oldest first. */
    val history: StateFlow<List<FirewallCounters.DayStats>> = _history.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // The history is saved by the VPN service; load it even when
            // protection is off so the card survives app restarts.
            FirewallCounters.init(context)
            while (isActive) {
                _history.value = FirewallCounters.history(FirewallCounters.HISTORY_DAYS)
                delay(HISTORY_REFRESH_MS)
            }
        }
    }

    private val _allowedDomains = MutableStateFlow<Set<String>>(emptySet())
    /** Domains allowed from the home in this session, hidden from the lists. */
    val allowedDomains: StateFlow<Set<String>> = _allowedDomains.asStateFlow()

    /** Add an allow rule for [domain] (and its subdomains) and reload the rules. */
    fun allowDomain(domain: String) {
        val value = domain.trim().trimEnd('.').lowercase()
        if (value.isEmpty()) return
        _allowedDomains.value = _allowedDomains.value + value
        viewModelScope.launch(Dispatchers.IO) {
            val exceptions = config.hosts.exceptions
            if (exceptions.none { it.data.equals(value, ignoreCase = true) && it.state == HostState.ALLOW }) {
                exceptions.removeAll { it.data.equals(value, ignoreCase = true) }
                exceptions.add(HostException(title = TITLE, data = value, state = HostState.ALLOW))
                config.save()
            }
            domainCacheService.invalidateCache()
            withContext(Dispatchers.Main) { domainCacheService.forceRefresh() }
            Logger.info("Home: allowed domain $value")
        }
    }

    companion object {
        const val TITLE = "Allowed from home"
        private const val HISTORY_REFRESH_MS = 60_000L
    }
}
