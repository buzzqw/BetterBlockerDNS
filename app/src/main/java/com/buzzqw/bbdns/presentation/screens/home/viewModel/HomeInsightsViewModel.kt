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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    /**
     * Daily allowed/blocked totals of the last 30 days, oldest first.
     * Refreshed only while the home is visible: the view model outlives the
     * screen when the app goes to the background.
     */
    val history: StateFlow<List<FirewallCounters.DayStats>> = flow {
        // The history is saved by the VPN service; load it even when
        // protection is off so the card survives app restarts.
        FirewallCounters.init(context)
        while (true) {
            emit(FirewallCounters.history(FirewallCounters.HISTORY_DAYS))
            delay(HISTORY_REFRESH_MS)
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _allowedDomains = MutableStateFlow<Set<String>>(emptySet())
    /** Domains allowed from the home in this session, hidden from the lists. */
    val allowedDomains: StateFlow<Set<String>> = _allowedDomains.asStateFlow()

    /**
     * Rules replaced by [allowDomain], so [undoAllowDomain] can put them back.
     * A null list means the allow rule already existed and nothing changed.
     */
    private val replacedRules = HashMap<String, List<HostException>?>()

    /** Serialises allow/undo: an undo tapped right away must run after the allow. */
    private val rulesMutex = Mutex()

    private fun normalize(domain: String) = domain.trim().trimEnd('.').lowercase()

    /** Add an allow rule for [domain] (and its subdomains) and reload the rules. */
    fun allowDomain(domain: String) {
        val value = normalize(domain)
        if (value.isEmpty()) return
        _allowedDomains.value = _allowedDomains.value + value
        viewModelScope.launch(Dispatchers.IO) {
            rulesMutex.withLock {
                val exceptions = config.hosts.exceptions
                if (exceptions.none { it.data.equals(value, ignoreCase = true) && it.state == HostState.ALLOW }) {
                    val replaced = exceptions.filter { it.data.equals(value, ignoreCase = true) }
                    exceptions.removeAll { it.data.equals(value, ignoreCase = true) }
                    exceptions.add(HostException(title = TITLE, data = value, state = HostState.ALLOW))
                    config.save()
                    replacedRules[value] = replaced
                } else {
                    replacedRules[value] = null
                }
                reloadRules()
            }
            Logger.info("Home: allowed domain $value")
        }
    }

    /** Revert the last [allowDomain] of [domain], restoring any rule it replaced. */
    fun undoAllowDomain(domain: String) {
        val value = normalize(domain)
        if (value.isEmpty()) return
        _allowedDomains.value = _allowedDomains.value - value
        viewModelScope.launch(Dispatchers.IO) {
            rulesMutex.withLock {
                if (!replacedRules.containsKey(value)) return@withLock
                val replaced = replacedRules.remove(value) ?: return@withLock
                val exceptions = config.hosts.exceptions
                exceptions.removeAll {
                    it.data.equals(value, ignoreCase = true) && it.state == HostState.ALLOW && it.title == TITLE
                }
                exceptions.addAll(replaced)
                config.save()
                reloadRules()
            }
            Logger.info("Home: undid allow of $value")
        }
    }

    private suspend fun reloadRules() {
        domainCacheService.invalidateCache()
        withContext(Dispatchers.Main) { domainCacheService.forceRefresh() }
    }

    companion object {
        const val TITLE = "Allowed from home"
        private const val HISTORY_REFRESH_MS = 60_000L
    }
}
