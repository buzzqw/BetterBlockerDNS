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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.data.cache.DomainCacheService
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import com.buzzqw.bbdns.presentation.config
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.components.DownloadState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.FileHelper
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostException
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostFile
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabaseUpdateWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HageziViewModel @Inject constructor(
    private val domainCacheService: DomainCacheService,
    private val preferencesUseCases: PreferencesUseCases,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)

    private val _enabledUrls = MutableStateFlow<Set<String>>(emptySet())
    val enabledUrls: StateFlow<Set<String>> = _enabledUrls.asStateFlow()

    private val _compatibilityEnabled = MutableStateFlow(false)
    val compatibilityEnabled: StateFlow<Boolean> = _compatibilityEnabled.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _downloadState = MutableStateFlow<DownloadState?>(null)
    val downloadState: StateFlow<DownloadState?> = _downloadState.asStateFlow()

    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog: StateFlow<Boolean> = _showDownloadDialog.asStateFlow()

    private val _currentListName = MutableStateFlow<String?>(null)
    val currentListName: StateFlow<String?> = _currentListName.asStateFlow()

    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    private val _stage = MutableStateFlow("")
    val stage: StateFlow<String> = _stage.asStateFlow()

    private val catalogue: List<BlocklistSource> = HageziCatalog.all + StevenBlackCatalog.all

    init {
        refreshEnabled()
        viewModelScope.launch(Dispatchers.IO) {
            // Migrate first, so seeding sees the user's converted selection.
            // A migrated user already has a selection: no default seeding.
            if (!migrateLegacyStevenBlack()) {
                if (!seedDefaultProtectionIfNeeded()) {
                    migrateOldRecommendedProfileToBalanced()
                }
            }
        }
    }

    /**
     * Earlier releases offered StevenBlack "alternates/<ext>/hosts" files,
     * which embed the whole Unified list. Replace them with Unified plus the
     * stackable "-only" extensions, keeping the same blocking coverage.
     */
    private suspend fun migrateLegacyStevenBlack(): Boolean {
        val legacy = config.hosts.items.filter {
            it.state == HostState.DENY && StevenBlackCatalog.legacyReplacement(it.data) != null
        }
        if (legacy.isEmpty()) return false
        val replacement = legacy
            .flatMap { StevenBlackCatalog.legacyReplacement(it.data).orEmpty() }
            .distinct()
        config.hosts.items.removeAll { item -> legacy.any { it.data == item.data } }
        config.save()
        legacy.forEach { item -> FileHelper.getItemFile(item)?.delete() }
        Logger.info("Migrating ${legacy.size} legacy StevenBlack list(s) to ${replacement.map { it.id }}")
        withContext(Dispatchers.Main) {
            refreshEnabled()
            addLists(replacement, displayName = StevenBlackCatalog.UNIFIED.name)
        }
        return true
    }

    /**
     * New installs get Balanced protection enabled once: the balanced HaGeZi
     * profile (Multi NORMAL + TIF mini) plus StevenBlack Unified. This keeps
     * both the malware and the ad presets visibly enabled by default.
     *
     * @return true when a default selection was applied.
     */
    private suspend fun seedDefaultProtectionIfNeeded(): Boolean {
        var seeded = false
        preferencesUseCases.loadSettings.execute().fold(
            ifSuccess = { settings ->
                if (settings.defaultBlocklistsSeeded) return@fold
                preferencesUseCases.saveSettings.execute(
                    settings.copy(defaultBlocklistsSeeded = true)
                )
                // Do not override a selection the user already made
                // (HaGeZi or StevenBlack) when upgrading.
                val catalogueUrls = HageziCatalog.all.map { it.url }.toSet() +
                    StevenBlackCatalog.all.map { it.url }
                val hasCatalogueList = _enabledUrls.value.any { it in catalogueUrls }
                if (!hasCatalogueList) {
                    val balanced = HageziCatalog.profiles.firstOrNull { it.id == "balanced" }
                    if (balanced != null) {
                        val lists = balanced.listIds.mapNotNull { HageziCatalog.byId[it] } +
                            StevenBlackCatalog.UNIFIED
                        withContext(Dispatchers.Main) {
                            applyExclusiveProfile(lists, balanced.name)
                        }
                        seeded = true
                    }
                }
            },
            ifFailure = { Logger.error("Cannot seed default blocklists: ${it.message}") }
        )
        return seeded
    }

    /**
     * Existing installs created before Balanced became the default still have
     * the old recommended Pro profile. Move them to Balanced once; a user who
     * chose another profile (or a custom mix) is left untouched.
     */
    private suspend fun migrateOldRecommendedProfileToBalanced() {
        val prefs = context.getSharedPreferences("bbdns_profile", Context.MODE_PRIVATE)
        if (prefs.getBoolean("balanced_default_applied", false)) return
        val current = HageziCatalog.matchingProfile(_enabledUrls.value)
        if (current?.id == "pro") {
            val balanced = HageziCatalog.profiles.firstOrNull { it.id == "balanced" }
            if (balanced != null) {
                withContext(Dispatchers.Main) { applyProfile(balanced) }
                Logger.info("Profile migrated from Pro to Balanced default")
            }
        }
        prefs.edit().putBoolean("balanced_default_applied", true).apply()
    }

    fun refreshEnabled() {
        _enabledUrls.value = config.hosts.items
            .filter { it.state == HostState.DENY }
            .map { it.data }
            .toSet()
        _compatibilityEnabled.value = config.hosts.exceptions.any {
            it.title == CompatibilityAllowlist.EXCEPTION_TITLE && it.state == HostState.ALLOW
        }
    }

    fun isEnabled(list: BlocklistSource): Boolean = list.url in _enabledUrls.value

    fun toggle(list: BlocklistSource, enabled: Boolean) {
        if (_busy.value) return

        if (enabled) {
            addLists(listOf(list), displayName = list.name)
        } else {
            removeList(list)
        }
    }

    fun applyProfile(profile: HageziProfile) {
        val lists = profile.listIds.mapNotNull { HageziCatalog.byId[it] }
        applyExclusiveProfile(lists, profile.name)
    }

    /**
     * A HaGeZi profile owns the Multi/TIF group: picking one replaces the
     * previous tiers, while StevenBlack Unified stays available as an extra
     * list (the malware preset adds it too).
     */
    private fun applyExclusiveProfile(lists: List<BlocklistSource>, displayName: String) {
        if (_busy.value) return
        if (lists.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            // Snapshot lets us restore the previous selection if the download fails.
            val snapshot = config.hosts.items.toList()

            // A profile owns the whole Multi/TIF group: remove the previous tiers first.
            val managedUrls = HageziCatalog.profileManagedIds
                .mapNotNull { HageziCatalog.byId[it]?.url }
                .toSet()
            config.hosts.items.removeAll { it.data in managedUrls }
            lists.forEach { list ->
                config.hosts.items.add(
                    HostFile(title = list.name, data = list.url, state = HostState.DENY)
                )
            }
            config.save()
            domainCacheService.invalidateCache()
            withContext(Dispatchers.Main) { refreshEnabled() }

            downloadAndApply(
                lists = lists,
                displayName = displayName,
                onFailure = {
                    config.hosts.items.clear()
                    config.hosts.items.addAll(snapshot)
                    config.save()
                    domainCacheService.invalidateCache()
                    refreshEnabled()
                }
            )
        }
    }

    fun toggleCompatibilityAllowlist(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            config.hosts.exceptions.removeAll {
                it.title == CompatibilityAllowlist.EXCEPTION_TITLE
            }
            if (enabled) {
                CompatibilityAllowlist.domains.forEach { domain ->
                    config.hosts.exceptions.add(
                        HostException(
                            title = CompatibilityAllowlist.EXCEPTION_TITLE,
                            data = domain,
                            state = HostState.ALLOW
                        )
                    )
                }
            }
            config.save()
            domainCacheService.invalidateCache()
            withContext(Dispatchers.Main) {
                refreshEnabled()
                domainCacheService.forceRefresh()
            }
            Logger.info("BBDNS compatibility allowlist enabled=$enabled")
        }
    }

    fun disableAll() {
        if (_busy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            val urls = (HageziCatalog.all.map { it.url } + StevenBlackCatalog.all.map { it.url }).toSet()
            val isCatalogue: (String) -> Boolean = { url ->
                url in urls || StevenBlackCatalog.legacyReplacement(url) != null
            }
            val removed = config.hosts.items.filter { isCatalogue(it.data) }
            config.hosts.items.removeAll { isCatalogue(it.data) }
            config.save()
            removed.forEach { item -> FileHelper.getItemFile(item)?.delete() }
            domainCacheService.invalidateCache()
            withContext(Dispatchers.Main) {
                refreshEnabled()
                domainCacheService.forceRefresh()
            }
            Logger.info("All catalogue lists disabled (${removed.size} removed)")
        }
    }

    fun dismissDownloadDialog() {
        _showDownloadDialog.value = false
        _downloadState.value = null
        _currentListName.value = null
        _progress.value = 0
        _stage.value = ""
    }

    private fun addLists(lists: List<BlocklistSource>, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // A smaller list already contained in an enabled profile must not
            // be downloaded again (e.g. Multi NORMAL inside Multi PRO).
            val effective = lists.filterNot {
                BlocklistCoverage.isCovered(it.url, _enabledUrls.value)
            }
            if (effective.isEmpty()) {
                Logger.info("Lists already included in the active profile: ${lists.map { it.name }}")
                withContext(Dispatchers.Main) { refreshEnabled() }
                return@launch
            }
            val snapshot = config.hosts.items.toList()
            // Alternatives (Multi tiers, full/medium/mini variants): enabling
            // one replaces the others of the same group.
            val groups = effective.mapNotNull { it.exclusiveGroup }.toSet()
            if (groups.isNotEmpty()) {
                val keep = effective.map { it.url }.toSet()
                val replaced = catalogue
                    .filter { it.exclusiveGroup in groups && it.url !in keep }
                    .map { it.url }
                    .toSet()
                config.hosts.items
                    .filter { it.data in replaced }
                    .forEach { item -> FileHelper.getItemFile(item)?.delete() }
                config.hosts.items.removeAll { it.data in replaced }
            }
            effective.forEach { list ->
                if (config.hosts.items.none { it.data == list.url }) {
                    config.hosts.items.add(
                        HostFile(title = list.name, data = list.url, state = HostState.DENY)
                    )
                } else {
                    // Re-enable a previously disabled entry.
                    config.hosts.items.forEach { item ->
                        if (item.data == list.url) item.state = HostState.DENY
                    }
                }
            }
            config.save()
            domainCacheService.invalidateCache()
            withContext(Dispatchers.Main) { refreshEnabled() }

            downloadAndApply(
                lists = effective,
                displayName = displayName,
                onFailure = {
                    config.hosts.items.clear()
                    config.hosts.items.addAll(snapshot)
                    config.save()
                    domainCacheService.invalidateCache()
                    refreshEnabled()
                }
            )
        }
    }

    private fun removeList(list: BlocklistSource) {
        viewModelScope.launch(Dispatchers.IO) {
            config.hosts.items.removeAll { it.data == list.url }
            config.save()

            // Remove the cached download as well.
            FileHelper.getItemFile(HostFile(title = list.name, data = list.url))?.delete()

            domainCacheService.invalidateCache()
            withContext(Dispatchers.Main) {
                refreshEnabled()
                domainCacheService.forceRefresh()
            }
            Logger.info("Blocklist disabled: ${list.name}")
        }
    }

    private suspend fun downloadAndApply(
        lists: List<BlocklistSource>,
        displayName: String,
        onFailure: suspend () -> Unit
    ) {
        val urls = lists.map { it.url }
        if (urls.isEmpty()) return

        _busy.value = true
        _downloadState.value = DownloadState.Downloading
        _showDownloadDialog.value = true
        _currentListName.value = displayName
        _progress.value = 0
        _stage.value = "downloading"

        val request = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>()
            .setInputData(workDataOf("target_lists" to urls.toTypedArray()))
            .build()

        workManager.enqueue(request)

        try {
            while (true) {
                val info = workManager.getWorkInfoById(request.id).get()
                if (info == null) {
                    _downloadState.value = DownloadState.Error
                    onFailure()
                    break
                }

                _progress.value = info.progress.getInt("progress", _progress.value)
                _stage.value = info.progress.getString("stage") ?: _stage.value

                if (info.state.isFinished) {
                    if (info.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                        _progress.value = 100
                        _stage.value = "complete"
                        _downloadState.value = DownloadState.Success
                        // The worker reloaded RuleDatabase directly; avoid a
                        // second full parse of every list.
                        domainCacheService.markInitialized()
                    } else {
                        _downloadState.value = DownloadState.Error
                        onFailure()
                    }
                    break
                }
                delay(400)
            }
        } catch (e: Exception) {
            Logger.error("HaGeZi update failed for $displayName: ${e.message}", e)
            _downloadState.value = DownloadState.Error
            onFailure()
        } finally {
            refreshEnabled()
            _busy.value = false
        }
    }
}
