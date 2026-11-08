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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.data.cache.DomainCacheService
import com.buzzqw.bbdns.presentation.config
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.BlockReason
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.BlockReasonFinder
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostException
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** State of the "Why is this blocked?" sheet. */
sealed interface BlockReasonUiState {
    data object Idle : BlockReasonUiState

    data class Searching(val domain: String, val scanned: Int, val total: Int) : BlockReasonUiState

    /** @param allowedNow true once the user allowed the domain from the sheet. */
    data class Ready(val reason: BlockReason, val allowedNow: Boolean = false) : BlockReasonUiState

    data class Failed(val domain: String) : BlockReasonUiState
}

@HiltViewModel
class BlockReasonViewModel @Inject constructor(
    private val finder: BlockReasonFinder,
    private val domainCacheService: DomainCacheService,
) : ViewModel() {

    private val _state = MutableStateFlow<BlockReasonUiState>(BlockReasonUiState.Idle)
    val state: StateFlow<BlockReasonUiState> = _state.asStateFlow()

    private var job: Job? = null

    /** Starts (or restarts) the search for [domain]. */
    fun load(domain: String) {
        val value = domain.trim().trimEnd('.').lowercase()
        job?.cancel()
        if (value.isEmpty()) {
            _state.value = BlockReasonUiState.Idle
            return
        }
        _state.value = BlockReasonUiState.Searching(value, 0, 0)
        job = viewModelScope.launch {
            _state.value = try {
                val reason = finder.find(value) { scanned, total ->
                    _state.value = BlockReasonUiState.Searching(value, scanned, total)
                }
                BlockReasonUiState.Ready(reason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.error("BlockReason: search failed for $value", e)
                BlockReasonUiState.Failed(value)
            }
        }
    }

    /** Cancels any running search and resets the sheet. */
    fun clear() {
        job?.cancel()
        job = null
        _state.value = BlockReasonUiState.Idle
    }

    /**
     * Adds an ALLOW user rule for [domain] and reloads the rules, like the
     * home dashboard does.
     */
    fun allowDomain(domain: String) {
        val value = domain.trim().trimEnd('.').lowercase()
        if (value.isEmpty()) return
        (_state.value as? BlockReasonUiState.Ready)?.let {
            if (it.reason.domain == value) _state.value = it.copy(allowedNow = true)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val exceptions = config.hosts.exceptions
            if (exceptions.none { it.data.equals(value, ignoreCase = true) && it.state == HostState.ALLOW }) {
                exceptions.removeAll { it.data.equals(value, ignoreCase = true) }
                exceptions.add(HostException(title = ALLOW_TITLE, data = value, state = HostState.ALLOW))
                config.save()
            }
            domainCacheService.invalidateCache()
            withContext(Dispatchers.Main) { domainCacheService.forceRefresh() }
            Logger.info("Logs: allowed domain $value")
        }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    companion object {
        /** Title stored with the user rule; not shown as UI text. */
        const val ALLOW_TITLE = "Allowed from logs"
    }
}
