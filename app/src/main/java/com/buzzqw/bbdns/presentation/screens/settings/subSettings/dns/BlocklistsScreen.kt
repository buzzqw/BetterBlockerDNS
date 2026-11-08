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


package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns

import android.annotation.SuppressLint
import android.content.Context
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Games
import androidx.compose.material.icons.rounded.Hardware
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Man
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.core.utils.constants.AppConstants
import com.buzzqw.bbdns.presentation.config
import com.buzzqw.bbdns.presentation.navigation.routes.SettingRoutes
import com.buzzqw.bbdns.presentation.screens.settings.components.CustomListDialog
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.components.DownloadDialog
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.components.DownloadState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.FileHelper
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostFile
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabaseUpdateWorker
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.BlocklistCoverage
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.BlocklistSelection
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.HageziCatalog
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.HageziProfile
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.HageziViewModel
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.viewModel.BlockListViewModel
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/**
 * Blocklists: the simple protection presets first (the easy choice), then the
 * advanced options (native HaGeZi/StevenBlack sources, custom lists, allow and
 * deny rules) and finally list updates and the blocked-domain count.
 * Split out of the former all-in-one DNS screen; the list logic is unchanged.
 */
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun BlocklistsScreen(
    navController: NavController,
    settings: SettingsViewModel,
    blockListViewModel: BlockListViewModel = hiltViewModel()
) {
    val doneNames = RuleDatabaseUpdateWorker.doneNames.collectAsState(initial = emptyList())
    val blockList = blockListViewModel.ruleDatabase.blockedHosts.collectAsState()
    val hageziViewModel: HageziViewModel = hiltViewModel()
    val enabledCatalogueUrls by hageziViewModel.enabledUrls.collectAsState()
    val hageziBusy by hageziViewModel.busy.collectAsState()
    val compatibilityEnabled by hageziViewModel.compatibilityEnabled.collectAsState()
    val hageziShowDownloadDialog by hageziViewModel.showDownloadDialog.collectAsState()
    val hageziDownloadState by hageziViewModel.downloadState.collectAsState()
    val hageziProgress by hageziViewModel.progress.collectAsState()
    val hageziStage by hageziViewModel.stage.collectAsState()
    val hageziCurrentName by hageziViewModel.currentListName.collectAsState()
    val activeProfile = HageziCatalog.matchingProfile(enabledCatalogueUrls)
    val context = LocalContext.current
    val isDomainsLoading by blockListViewModel.isLoading.collectAsState()
    val isDomainsInitialized by blockListViewModel.isInitialized.collectAsState()
    val showDownloadDialog by blockListViewModel.showDownloadDialog.collectAsState()
    val downloadState by blockListViewModel.downloadState.collectAsState()
    val downloadProgress by blockListViewModel.downloadProgress.collectAsState()
    val downloadStage by blockListViewModel.downloadStage.collectAsState()
    val currentDownloadingRule by blockListViewModel.currentDownloadingRule.collectAsState()

    // Initialize doneNames with existing URLs from config (for migration support)
    LaunchedEffect(Unit) {
        val existingUrls = config.hosts.items.map { it.data }.filter { it.isNotEmpty() }
        if (existingUrls.isNotEmpty() && doneNames.value.isEmpty()) {
            RuleDatabaseUpdateWorker.doneNames.value = existingUrls.toMutableList()
            Logger.info("Initialized doneNames with ${existingUrls.size} URLs from config")
        }

        // Malware protection now bundles StevenBlack Unified. Add it once to
        // installations where malware protection was already enabled.
        val migrationPrefs = context.getSharedPreferences("bbdns_migrations", Context.MODE_PRIVATE)
        if (!migrationPrefs.getBoolean("malware_stevenblack_added", false)) {
            val malwareEnabled = AppConstants.DnsBlockLists.MALWARE_PROTECTION.any {
                it in doneNames.value
            } || AppConstants.DnsBlockLists.Legacy.MALWARE_PROTECTION.any {
                it in doneNames.value
            }
            if (malwareEnabled) {
                val missing = AppConstants.DnsBlockLists.MALWARE_PROTECTION.filter { url ->
                    config.hosts.items.none { it.data == url }
                }
                if (missing.isNotEmpty()) {
                    missing.forEach { url -> config.addURL(url, url, HostState.DENY) }
                    // Enabling the malware preset must replace a smaller TIF
                    // variant that a profile already enabled.
                    val redundant = BlocklistSelection.sanitize(config.hosts.items)
                    redundant.forEach { item -> FileHelper.getItemFile(item)?.delete() }
                    config.save()
                    val inputData = Data.Builder()
                        .putStringArray("target_lists", missing.toTypedArray())
                        .build()
                    WorkManager.getInstance(context).enqueue(
                        OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>()
                            .setInputData(inputData)
                            .build()
                    )
                    Logger.info("Malware preset migration: added ${missing.size} missing list(s)")
                }
            }
            migrationPrefs.edit().putBoolean("malware_stevenblack_added", true).apply()
        }
    }

    // Local state to track immediate switch changes for UI responsiveness
    val localSwitchStates = remember { mutableStateMapOf<String, Boolean>() }
    
    // Track if we're actively updating domains (different from initial load)
    var isDomainUpdateInProgress by remember { mutableStateOf(false) }

    // Update auto-update interval and enabled state when the screen loads (in case settings changed)
    LaunchedEffect(settings.settings.value.autoUpdateInterval, settings.settings.value.autoUpdateEnabled) {
        AutoUpdateManager.scheduleAutoUpdateWorker(
            context,
            settings.settings.value.autoUpdateInterval,
            enabled = settings.settings.value.autoUpdateEnabled
        )
    }

    fun updateLists(onComplete: ((Boolean) -> Unit)? = null) {
        val workRequest = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
        
        // Monitor work completion
        WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.id)
            .observeForever { workInfo ->
                if (workInfo?.state?.isFinished == true) {
                    onComplete?.invoke(workInfo.state == WorkInfo.State.SUCCEEDED)
                }
            }
    }
    
    fun updateSpecificList(targetList: String, onComplete: ((Boolean) -> Unit)? = null) {
        val inputData = Data.Builder()
            .putStringArray("target_lists", arrayOf(targetList))
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>()
            .setInputData(inputData)
            .build()
            
        WorkManager.getInstance(context).enqueue(workRequest)
        
        onComplete?.let { callback ->
            WorkManager.getInstance(context)
                .getWorkInfoByIdLiveData(workRequest.id)
                .observeForever { workInfo ->
                    if (workInfo != null) {
                        // Update progress
                        val progress = workInfo.progress.getInt("progress", 0)
                        val stage = workInfo.progress.getString("stage") ?: ""
                        blockListViewModel.updateProgress(progress, stage)

                        // Check if finished
                        if (workInfo.state.isFinished) {
                            callback(workInfo.state == WorkInfo.State.SUCCEEDED)
                        }
                    }
                }
        }
    }

    fun updateList(enabled: Boolean, lists: List<String>, listKey: String) {
        if (lists.isEmpty()) {
            Logger.error("updateList: Lists parameter is empty")
            return
        }

        // Don't update switch state immediately - only update after successful download/removal

        // Migration: remove every URL used by older releases for this list type.
        val legacyUrls = when (listKey) {
            "MALWARE_PROTECTION" -> AppConstants.DnsBlockLists.Legacy.MALWARE_PROTECTION
            "AD_PROTECTION" -> AppConstants.DnsBlockLists.Legacy.AD_PROTECTION
            "ADS_EXTREME" -> AppConstants.DnsBlockLists.Legacy.ADS_EXTREME
            "PRIVACY_PROTECTION" -> AppConstants.DnsBlockLists.Legacy.PRIVACY_PROTECTION
            "GAMBLING_PROTECTION" -> AppConstants.DnsBlockLists.Legacy.GAMBLING_PROTECTION
            "ADULT_PROTECTION" -> AppConstants.DnsBlockLists.Legacy.ADULT_PROTECTION
            "SOCIAL_PROTECTION" -> AppConstants.DnsBlockLists.Legacy.SOCIAL_PROTECTION
            else -> emptyList()
        }

        // A list already contained in the active profile must not be downloaded
        // again (e.g. Multi NORMAL after Multi PRO).
        val toAdd = if (enabled) {
            val enabledUrls = config.hosts.items
                .filter { it.state == HostState.DENY }
                .map { it.data }
                .toSet()
            lists.filterNot { it in enabledUrls || BlocklistCoverage.isCovered(it, enabledUrls) }
        } else {
            emptyList()
        }
        if (enabled && toAdd.isEmpty()) {
            localSwitchStates[listKey] = true
            Logger.info("Preset $listKey already included in the active profile")
            return
        }

        // Show download dialog for enabling rules
            if (enabled) {
                val ruleName = when {
                    toAdd.any { it in AppConstants.DnsBlockLists.MALWARE_PROTECTION } -> "Malware Protection"
                    toAdd.any { it in AppConstants.DnsBlockLists.AD_PROTECTION } -> "Ad Protection"
                    toAdd.any { it in AppConstants.DnsBlockLists.ADS_EXTREME } -> "Ads Extreme"
                    toAdd.any { it in AppConstants.DnsBlockLists.PRIVACY_PROTECTION } -> "Privacy Protection"
                    toAdd.any { it in AppConstants.DnsBlockLists.SOCIAL_PROTECTION } -> "Social Media"
                    toAdd.any { it in AppConstants.DnsBlockLists.ADULT_PROTECTION } -> "Adult Content"
                    toAdd.any { it in AppConstants.DnsBlockLists.GAMBLING_PROTECTION } -> "Gambling"
                    else -> "Custom Rules"
                }
                blockListViewModel.startDownload(ruleName)
            }

            CoroutineScope(Dispatchers.IO).launch {
                var selectionSnapshot: List<HostFile>? = null
                var replacedUrls: Set<String> = emptySet()
                try {
                    withContext(Dispatchers.Main) {
                        isDomainUpdateInProgress = true
                    }

                    // Migration: remove legacy URLs (applies to both enable and disable).
                    legacyUrls.forEach { legacyUrl ->
                        if (legacyUrl in doneNames.value || config.hosts.items.any { it.data == legacyUrl }) {
                            Logger.info("Migration: Removing legacy URL: $legacyUrl")
                            config.removeURL(legacyUrl)
                            RuleDatabaseUpdateWorker.doneNames.value =
                                RuleDatabaseUpdateWorker.doneNames.value
                                    .filter { it != legacyUrl }
                                    .toMutableList()
                            FileHelper.getItemFile(HostFile(title = legacyUrl, data = legacyUrl))?.delete()
                        }
                    }

                    if (enabled) {
                        // Snapshot the selection after legacy cleanup: a failed
                        // download restores it exactly, including alternatives
                        // that were replaced below.
                        selectionSnapshot = config.hosts.items.toList()

                        // Alternatives (TIF full/medium/mini, Multi tiers, DoH
                        // and gambling variants) replace each other instead of
                        // being stacked.
                        replacedUrls = BlocklistSelection.conflictsWhenEnabling(
                            urls = toAdd,
                            currentUrls = config.hosts.items.map { it.data },
                        )
                        replacedUrls.forEach { url ->
                            Logger.info("Replacing conflicting list: $url")
                            config.removeURL(url)
                            RuleDatabaseUpdateWorker.doneNames.value =
                                RuleDatabaseUpdateWorker.doneNames.value
                                    .filter { it != url }
                                    .toMutableList()
                        }

                        // Add all URLs in the list, reusing an entry already
                        // provided by the active profile instead of duplicating it.
                        toAdd.forEach { url ->
                            val existing = config.hosts.items.firstOrNull { it.data == url }
                            if (existing == null) {
                                config.addURL(url, url, HostState.DENY)
                                Logger.info("Added list: $url")
                            } else {
                                existing.state = HostState.DENY
                                Logger.info("List already configured, re-enabled: $url")
                            }
                        }
                    } else {
                        // For disabling, remove all URLs in the list
                        lists.forEach { url ->
                            RuleDatabaseUpdateWorker.doneNames.value =
                                RuleDatabaseUpdateWorker.doneNames.value
                                    .filter { it != url }
                                    .toMutableList()
                            config.removeURL(url)
                            FileHelper.getItemFile(HostFile(title = url, data = url))?.delete()
                            Logger.info("Removed list: $url")
                        }
                        // Update switch state immediately for disabling
                        withContext(Dispatchers.Main) {
                            localSwitchStates[listKey] = false
                        }
                    }
                    config.save()  // Persist changes to disk
                    blockListViewModel.invalidateDomainsCache() // Invalidate cache for next load

                    withContext(Dispatchers.Main) {
                        if (enabled) {
                            // Update all lists in the array
                            val inputData = Data.Builder()
                                .putStringArray("target_lists", toAdd.toTypedArray())
                                .build()

                            val workRequest = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>()
                                .setInputData(inputData)
                                .build()

                            WorkManager.getInstance(context).enqueue(workRequest)

                            WorkManager.getInstance(context)
                                .getWorkInfoByIdLiveData(workRequest.id)
                                .observeForever { workInfo ->
                                    if (workInfo != null) {
                                        // Update progress
                                        val progress = workInfo.progress.getInt("progress", 0)
                                        val stage = workInfo.progress.getString("stage") ?: ""
                                        blockListViewModel.updateProgress(progress, stage)

                                        // Check if finished
                                        if (workInfo.state.isFinished) {
                                            if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                                                // Only turn switch ON after successful download
                                                localSwitchStates[listKey] = true
                                                blockListViewModel.downloadSuccess()
                                                // The worker reloaded RuleDatabase once:
                                                // do not trigger a second full parse.
                                                blockListViewModel.markDomainsInitialized()
                                                // The replaced alternatives are no
                                                // longer needed: drop their files.
                                                replacedUrls.forEach { url ->
                                                    FileHelper.getItemFile(
                                                        HostFile(title = url, data = url)
                                                    )?.delete()
                                                }
                                            } else {
                                                // Download failed, restore the selection
                                                // that existed before the replacements.
                                                val previous = selectionSnapshot
                                                if (previous != null) {
                                                    CoroutineScope(Dispatchers.IO).launch {
                                                        try {
                                                            config.hosts.items.clear()
                                                            config.hosts.items.addAll(previous)
                                                            config.save()
                                                            Logger.info("Reverted list changes")
                                                        } catch (e: Exception) {
                                                            Logger.error("Failed to revert config: ${e.message}", e)
                                                        }
                                                    }
                                                }

                                                // Show appropriate error
                                                if (!blockListViewModel.isNetworkAvailable()) {
                                                    blockListViewModel.downloadNetworkError()
                                                } else {
                                                    blockListViewModel.downloadError()
                                                }
                                            }
                                            isDomainUpdateInProgress = false
                                        }
                                    }
                                }
                        } else {
                            // For disabling there is no download: the reload
                            // below removes the list from the rule database.
                            isDomainUpdateInProgress = false
                        }
                    }

                    // For disabling, no download is needed: refresh once.
                    if (!enabled) {
                        blockListViewModel.refreshDomains()
                    }
                    // For enabling, the download happens in updateLists() callback

                } catch (e: Exception) {
                    Logger.error("Failed to update lists: ${e.message}", e)

                    // Restore the previous selection if the failure happened
                    // after the conflicting alternatives were removed.
                    selectionSnapshot?.let { previous ->
                        config.hosts.items.clear()
                        config.hosts.items.addAll(previous)
                        config.save()
                    }

                    withContext(Dispatchers.Main) {
                        isDomainUpdateInProgress = false

                        // Reset switch state to previous value on error
                        val previousState = lists.any { it in doneNames.value }
                        localSwitchStates[listKey] = previousState

                        // Show appropriate error dialog
                        if (enabled) {
                            if (!blockListViewModel.isNetworkAvailable()) {
                                blockListViewModel.downloadNetworkError()
                            } else if (blockListViewModel.isNetworkError(e)) {
                                blockListViewModel.downloadNetworkError()
                            } else {
                                blockListViewModel.downloadError()
                            }
                        }
                    }
                }
            }
    }

    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings_blocklists),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer(titleRes = R.string.blocklists_section_profile) {
            ProtectionProfileSelector(
                activeProfile = activeProfile,
                enabled = !hageziBusy,
                onProfileSelected = { profile -> hageziViewModel.applyProfile(profile) },
            )
            SettingsBox(
                title = stringResource(R.string.blocklists_compatibility_title),
                description = stringResource(R.string.blocklists_compatibility_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Security),
                actionType = SettingType.SWITCH,
                isUsable = !hageziBusy,
                variable = compatibilityEnabled,
                onSwitchEnabled = { enabled ->
                    if (!hageziBusy) hageziViewModel.toggleCompatibilityAllowlist(enabled)
                }
            )
        }
        item {
            Text(
                text = stringResource(R.string.blocklists_profile_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        settingsContainer(titleRes = R.string.blocklists_section_presets) {
            LaunchedEffect(Unit) {
                // Use the cached load: the update worker already reloads the
                // rules after every download, so no forced re-parse here.
                blockListViewModel.ensureDomainsLoaded()
            }

            val malwareEnabled = localSwitchStates["MALWARE_PROTECTION"]
                ?: (AppConstants.DnsBlockLists.TIF_ALL.any { it in enabledCatalogueUrls }
                    || AppConstants.DnsBlockLists.Legacy.MALWARE_PROTECTION.any { it in doneNames.value })
            val adCovering = BlocklistCoverage.coveringName(
                AppConstants.DnsBlockLists.AD_PROTECTION.first(),
                enabledCatalogueUrls,
            )
            val adEnabled = localSwitchStates["AD_PROTECTION"]
                ?: (AppConstants.DnsBlockLists.AD_PROTECTION.any { it in enabledCatalogueUrls }
                    || AppConstants.DnsBlockLists.CORE_MULTI.any { it in enabledCatalogueUrls }
                    || AppConstants.DnsBlockLists.Legacy.AD_PROTECTION.any { it in doneNames.value })

            SettingsBox(
                title = stringResource(id = R.string.dns_malware_protection),
                description = stringResource(id = R.string.dns_malware_protection_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Security),
                actionType = SettingType.SWITCH,
                variable = malwareEnabled,
                onSwitchEnabled = { enabled ->
                    updateList(
                        enabled,
                        if (enabled) {
                            AppConstants.DnsBlockLists.MALWARE_PROTECTION
                        } else {
                            AppConstants.DnsBlockLists.TIF_ALL
                        },
                        "MALWARE_PROTECTION",
                    )
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.dns_ad_protection),
                description = stringResource(id = R.string.dns_ad_blocker_desc) +
                    (adCovering?.let { "\n" + stringResource(R.string.blocklists_included_in, it) } ?: ""),
                icon = IconType.VectorIcon(Icons.Rounded.AdsClick),
                actionType = SettingType.SWITCH,
                isUsable = adCovering == null,
                variable = adEnabled,
                onNotUsableClicked = {
                    adCovering?.let {
                        Toast.makeText(
                            context,
                            context.getString(R.string.blocklists_included_in, it),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onSwitchEnabled = { enabled ->
                    if (adCovering != null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.blocklists_included_in, adCovering),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        if (!enabled) {
                            // Disabling ads also disables the aggressive tiers.
                            localSwitchStates["ADS_EXTREME"] = false
                            updateList(false, AppConstants.DnsBlockLists.ADS_EXTREME_ALL, "ADS_EXTREME")
                        }
                        updateList(
                            enabled,
                            if (enabled) {
                                AppConstants.DnsBlockLists.AD_PROTECTION
                            } else {
                                AppConstants.DnsBlockLists.CORE_MULTI
                            },
                            "AD_PROTECTION",
                        )
                    }
                }
            )
            
            // Ads Extreme setting - only show when Ad Protection is enabled
            val isAdProtectionEnabled = adEnabled
            
            if (isAdProtectionEnabled) {
                val extremeCovering = BlocklistCoverage.coveringName(
                    AppConstants.DnsBlockLists.ADS_EXTREME.first(),
                    enabledCatalogueUrls,
                )
                val extremeEnabled = localSwitchStates["ADS_EXTREME"]
                    ?: AppConstants.DnsBlockLists.ADS_EXTREME_ALL.any { it in enabledCatalogueUrls }
                SettingsBox(
                    title = stringResource(id = R.string.dns_ads_extreme),
                    description = stringResource(id = R.string.dns_ads_extreme_desc) +
                        (extremeCovering?.let { "\n" + stringResource(R.string.blocklists_included_in, it) } ?: ""),
                    icon = IconType.VectorIcon(Icons.Rounded.Hardware),
                    actionType = SettingType.SWITCH,
                    isUsable = extremeCovering == null,
                    variable = extremeEnabled,
                    onNotUsableClicked = {
                        extremeCovering?.let {
                            Toast.makeText(
                                context,
                                context.getString(R.string.blocklists_included_in, it),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onSwitchEnabled = { enabled ->
                        if (extremeCovering != null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.blocklists_included_in, extremeCovering),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            updateList(
                                enabled,
                                if (enabled) {
                                    AppConstants.DnsBlockLists.ADS_EXTREME
                                } else {
                                    AppConstants.DnsBlockLists.ADS_EXTREME_ALL
                                },
                                "ADS_EXTREME",
                            )
                        }
                    }
                )
            }
            val privacyCovering = BlocklistCoverage.coveringName(
                AppConstants.DnsBlockLists.PRIVACY_PROTECTION.first(),
                enabledCatalogueUrls,
            )
            val privacyEnabled = localSwitchStates["PRIVACY_PROTECTION"]
                ?: (AppConstants.DnsBlockLists.PRIVACY_ALL.any { it in enabledCatalogueUrls }
                    || AppConstants.DnsBlockLists.Legacy.PRIVACY_PROTECTION.any { it in doneNames.value })
            SettingsBox(
                title = stringResource(id = R.string.dns_block_trackers),
                description = stringResource(id = R.string.dns_block_trackers_desc) +
                    (privacyCovering?.let { "\n" + stringResource(R.string.blocklists_included_in, it) } ?: ""),
                icon = IconType.VectorIcon(Icons.Rounded.RemoveRedEye),
                actionType = SettingType.SWITCH,
                isUsable = privacyCovering == null,
                variable = privacyEnabled,
                onNotUsableClicked = {
                    privacyCovering?.let {
                        Toast.makeText(
                            context,
                            context.getString(R.string.blocklists_included_in, it),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                onSwitchEnabled = { enabled ->
                    if (privacyCovering != null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.blocklists_included_in, privacyCovering),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        updateList(
                            enabled,
                            if (enabled) {
                                AppConstants.DnsBlockLists.PRIVACY_PROTECTION
                            } else {
                                AppConstants.DnsBlockLists.PRIVACY_ALL
                            },
                            "PRIVACY_PROTECTION",
                        )
                    }
                }
            )
        }
        settingsContainer(titleRes = R.string.blocklists_section_content) {
            SettingsBox(
                title = stringResource(id = R.string.dns_social_media),
                description = stringResource(id = R.string.dns_social_media_desc),
                icon = IconType.VectorIcon(Icons.AutoMirrored.Rounded.Message),
                actionType = SettingType.SWITCH,
                variable = localSwitchStates["SOCIAL_PROTECTION"]
                    ?: (AppConstants.DnsBlockLists.SOCIAL_PROTECTION.any { it in doneNames.value }
                        || AppConstants.DnsBlockLists.Legacy.SOCIAL_PROTECTION.any { it in doneNames.value }),
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.SOCIAL_PROTECTION, "SOCIAL_PROTECTION")
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.dns_adult_content),
                description = stringResource(id = R.string.dns_block_adult),
                icon = IconType.VectorIcon(Icons.Rounded.Man),
                actionType = SettingType.SWITCH,
                variable = localSwitchStates["ADULT_PROTECTION"]
                    ?: (AppConstants.DnsBlockLists.ADULT_PROTECTION.any { it in doneNames.value }
                        || AppConstants.DnsBlockLists.Legacy.ADULT_PROTECTION.any { it in doneNames.value }),
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.ADULT_PROTECTION, "ADULT_PROTECTION")
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.dns_gambling),
                description = stringResource(id = R.string.dns_block_gambling),
                icon = IconType.VectorIcon(Icons.Rounded.Games),
                actionType = SettingType.SWITCH,
                variable = localSwitchStates["GAMBLING_PROTECTION"]
                    ?: (AppConstants.DnsBlockLists.GAMBLING_PROTECTION.any { it in doneNames.value }
                        || AppConstants.DnsBlockLists.Legacy.GAMBLING_PROTECTION.any { it in doneNames.value }),
                onSwitchEnabled = {
                    updateList(it, AppConstants.DnsBlockLists.GAMBLING_PROTECTION, "GAMBLING_PROTECTION")
                }
            )
        }
        settingsContainer(titleRes = R.string.blocklists_section_advanced) {
            SettingsBox(
                title = stringResource(R.string.blocklists_sources_title),
                description = stringResource(R.string.blocklists_sources_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Shield),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    navController.navigate(SettingRoutes.Hagezi.route)
                    onExit()
                }
            )
            val customBlocklistTitle = stringResource(id = R.string.blocklist_custom)
            val customBlocklistDescription = stringResource(R.string.blocklist_custom_desc)
            
            SettingsBox(
                title = customBlocklistTitle,
                description = customBlocklistDescription,
                icon = IconType.VectorIcon(Icons.Rounded.Add),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    navController.navigate(SettingRoutes.CustomBlocklist.route)
                    onExit()
                },
            )
            val rulesStatus by blockListViewModel.firewallManager.rulesLoaded.collectAsState()
            val isProtectionOnline = rulesStatus.name() == "ONLINE"
            
            SettingsBox(
                title = stringResource(R.string.dns_domain_management_title),
                description = stringResource(R.string.dns_domain_management_desc),
                icon = IconType.VectorIcon(Icons.Rounded.List),
                actionType = SettingType.CUSTOM,
                isUsable = !isProtectionOnline,
                onNotUsableClicked = {
                    // Show message why Domain Management is not available
                    blockListViewModel.showDomainManagementDisabledMessage()
                },
                customAction = { onExit ->
                    navController.navigate(SettingRoutes.DomainManagement.route)
                    onExit()
                }
            )
        }
        settingsContainer(titleRes = R.string.blocklists_section_updates) {
            // Auto-update interval setting
            val autoUpdateIntervals = listOf(
                6 * 60 * 60 * 1000L to stringResource(R.string.dns_auto_update_interval_6h),
                12 * 60 * 60 * 1000L to stringResource(R.string.dns_auto_update_interval_12h), 
                24 * 60 * 60 * 1000L to stringResource(R.string.dns_auto_update_interval_24h),
                3 * 24 * 60 * 60 * 1000L to stringResource(R.string.dns_auto_update_interval_3d),
                7 * 24 * 60 * 60 * 1000L to stringResource(R.string.dns_auto_update_interval_1w)
            )
            
            var showAutoUpdateDialog by remember { mutableStateOf(false) }
            
            // Function to get display text for current interval
            fun getCurrentIntervalText(): String {
                val currentInterval = settings.settings.value.autoUpdateInterval
                return autoUpdateIntervals.find { it.first == currentInterval }?.second
                    ?: if (currentInterval % (24 * 60 * 60 * 1000L) == 0L) {
                        context.getString(R.string.blocklists_interval_days, currentInterval / (24 * 60 * 60 * 1000L))
                    } else {
                        context.getString(R.string.blocklists_interval_hours, currentInterval / (60 * 60 * 1000L))
                    }
            }
            
            val autoUpdateText = if (settings.settings.value.autoUpdateEnabled) {
                stringResource(R.string.dns_auto_update_desc, getCurrentIntervalText())
            } else {
                stringResource(R.string.dns_auto_update_disabled_desc)
            }
            val lastUpdate = BlocklistUpdateStatus.lastUpdate(context)
            val lastUpdateValue = lastUpdate?.let { timestamp ->
                DateFormat.getDateTimeInstance(
                    DateFormat.MEDIUM,
                    DateFormat.SHORT,
                ).format(Date(timestamp))
            } ?: stringResource(R.string.dns_auto_update_never)
            val autoUpdateStatus = autoUpdateText + "\n" +
                stringResource(R.string.dns_auto_update_last_update, lastUpdateValue)

            SettingsBox(
                title = stringResource(R.string.dns_auto_update_title),
                description = autoUpdateStatus,
                icon = IconType.VectorIcon(Icons.Rounded.Schedule),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    showAutoUpdateDialog = true
                    onExit()
                }
            )

            if (showAutoUpdateDialog) {
                CustomListDialog(
                    text = stringResource(R.string.dns_auto_update_dialog_title),
                    onExit = { showAutoUpdateDialog = false }
                ) {
                    // Enable/Disable toggle
                    item {
                        SettingsBox(
                            title = stringResource(R.string.dns_auto_update_enabled_title),
                            description = stringResource(R.string.dns_auto_update_enabled_desc),
                            actionType = SettingType.SWITCH,
                            variable = settings.settings.value.autoUpdateEnabled,
                            onSwitchEnabled = { enabled ->
                                settings.update(settings.settings.value.copy(autoUpdateEnabled = enabled))
                                if (enabled) {
                                    AutoUpdateManager.scheduleAutoUpdateWorker(
                                        context,
                                        settings.settings.value.autoUpdateInterval,
                                        enabled = true
                                    )
                                } else {
                                    AutoUpdateManager.cancelAutoUpdate(context)
                                }
                            }
                        )
                    }

                    // Interval options (only shown when enabled)
                    if (settings.settings.value.autoUpdateEnabled) {
                        autoUpdateIntervals.forEach { (interval, label) ->
                            item {
                                SettingsBox(
                                    size = 8.dp,
                                    title = label,
                                    description = stringResource(R.string.dns_auto_update_description_template, label.lowercase()),
                                    actionType = SettingType.RADIOBUTTON,
                                    variable = settings.settings.value.autoUpdateInterval == interval,
                                    onSwitchEnabled = {
                                        if (it) {
                                            settings.update(settings.settings.value.copy(autoUpdateInterval = interval))
                                            AutoUpdateManager.scheduleAutoUpdateWorker(
                                                context,
                                                interval,
                                                enabled = settings.settings.value.autoUpdateEnabled
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            SettingsBox(
                title = stringResource(id = R.string.dns_blocked_domains),
                description = run {
                    val currentCount = blockList.value.value.count() + blockListViewModel.ruleDatabase.wildcardDomainCount
                    when {
                        currentCount > 0 -> {
                            val formatter = java.text.DecimalFormat("#,###")
                            stringResource(R.string.blocklists_count_blocked, formatter.format(currentCount))
                        }
                        isDomainUpdateInProgress -> stringResource(R.string.blocklists_count_updating)
                        isDomainsLoading && !isDomainsInitialized -> stringResource(R.string.blocklists_count_loading)
                        else -> stringResource(R.string.blocklists_count_none)
                    }
                },
                icon = IconType.VectorIcon(Icons.Rounded.Numbers),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    navController.navigate(SettingRoutes.ActiveLists.route)
                    onExit()
                },
            )
        }
    }

    // Download Dialog
    DownloadDialog(
        title = currentDownloadingRule?.let { ruleDisplayName(it) } ?: stringResource(R.string.blocklists_downloading_rules),
        isVisible = showDownloadDialog,
        downloadState = downloadState ?: DownloadState.Downloading,
        progress = downloadProgress,
        stage = downloadStage,
        onDismiss = { blockListViewModel.dismissDownloadDialog() },
        onRetry = {
            currentDownloadingRule?.let { ruleName ->
                val (listConstants, listKey) = when (ruleName) {
                    "Malware Protection" -> AppConstants.DnsBlockLists.MALWARE_PROTECTION to "MALWARE_PROTECTION"
                    "Ad Protection" -> AppConstants.DnsBlockLists.AD_PROTECTION to "AD_PROTECTION"
                    "Ads Extreme" -> AppConstants.DnsBlockLists.ADS_EXTREME to "ADS_EXTREME"
                    "Privacy Protection" -> AppConstants.DnsBlockLists.PRIVACY_PROTECTION to "PRIVACY_PROTECTION"
                    "Social Media" -> AppConstants.DnsBlockLists.SOCIAL_PROTECTION to "SOCIAL_PROTECTION"
                    "Adult Content" -> AppConstants.DnsBlockLists.ADULT_PROTECTION to "ADULT_PROTECTION"
                    "Gambling" -> AppConstants.DnsBlockLists.GAMBLING_PROTECTION to "GAMBLING_PROTECTION"
                    else -> emptyList<String>() to ""
                }
                if (listConstants.isNotEmpty()) {
                    updateList(true, listConstants, listKey)
                }
            }
        }
    )

    // Profile download dialog (the profile applies HaGeZi lists directly).
    DownloadDialog(
        title = hageziCurrentName ?: stringResource(R.string.blocklists_downloading_rules),
        isVisible = hageziShowDownloadDialog,
        downloadState = hageziDownloadState ?: DownloadState.Downloading,
        progress = hageziProgress,
        stage = hageziStage,
        onDismiss = { hageziViewModel.dismissDownloadDialog() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectionProfileSelector(
    activeProfile: HageziProfile?,
    enabled: Boolean,
    onProfileSelected: (HageziProfile) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        OutlinedTextField(
            value = activeProfile?.let { stringResource(it.nameRes) }
                ?: stringResource(R.string.blocklists_profile_custom),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(stringResource(R.string.blocklists_profile_title)) },
            supportingText = activeProfile?.let { profile ->
                { Text(stringResource(profile.descriptionRes)) }
            },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
        ) {
            HageziCatalog.profiles.forEach { profile ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(profile.nameRes),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    trailingIcon = {
                        if (activeProfile?.id == profile.id) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        if (activeProfile?.id != profile.id) {
                            onProfileSelected(profile)
                        }
                    },
                )
            }
        }
    }
}

/** Localised title for the internal download keys used by [BlocklistsScreen] (keys stay English for retry lookup). */
@Composable
private fun ruleDisplayName(ruleKey: String): String = when (ruleKey) {
    "Malware Protection" -> stringResource(R.string.dns_malware_protection)
    "Ad Protection" -> stringResource(R.string.dns_ad_protection)
    "Ads Extreme" -> stringResource(R.string.dns_ads_extreme)
    "Privacy Protection" -> stringResource(R.string.dns_privacy_protection)
    "Social Media" -> stringResource(R.string.dns_social_media)
    "Adult Content" -> stringResource(R.string.dns_adult_content)
    "Gambling" -> stringResource(R.string.dns_gambling)
    "Custom Rules" -> stringResource(R.string.dns_custom_rules)
    else -> ruleKey
}
