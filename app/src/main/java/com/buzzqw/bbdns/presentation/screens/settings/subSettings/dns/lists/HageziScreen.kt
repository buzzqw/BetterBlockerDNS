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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.constants.ProjectConstants
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.components.DownloadDialog
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel

@Composable
fun HageziScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
    hageziViewModel: HageziViewModel = hiltViewModel()
) {
    val uriHandler = LocalUriHandler.current
    val enabledUrls by hageziViewModel.enabledUrls.collectAsState()
    val compatibilityEnabled by hageziViewModel.compatibilityEnabled.collectAsState()
    val busy by hageziViewModel.busy.collectAsState()
    val showDownloadDialog by hageziViewModel.showDownloadDialog.collectAsState()
    val downloadState by hageziViewModel.downloadState.collectAsState()
    val progress by hageziViewModel.progress.collectAsState()
    val stage by hageziViewModel.stage.collectAsState()
    val currentListName by hageziViewModel.currentListName.collectAsState()

    val activeProfile = HageziCatalog.matchingProfile(enabledUrls)
    var showOptionalLists by rememberSaveable { mutableStateOf(false) }

    SettingsScaffold(
        settings = settingsViewModel,
        title = stringResource(R.string.blocklists_sources_title),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
            SettingsBox(
                title = stringResource(R.string.hagezi_native_catalogues_title),
                description = stringResource(R.string.hagezi_native_catalogues_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Shield),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.HAGEZI_REPO) }
            )
            SettingsBox(
                title = stringResource(R.string.hagezi_stevenblack_hosts_title),
                description = stringResource(R.string.hagezi_stevenblack_hosts_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Public),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.STEVENBLACK_REPO) }
            )
        }

        item {
            ToolSectionHeader(
                title = stringResource(R.string.hagezi_profiles_title),
                subtitle = stringResource(R.string.hagezi_profiles_desc)
            )
        }

        settingsContainer {
            HageziCatalog.profiles.forEach { profile ->
                SettingsBox(
                    title = stringResource(profile.nameRes).let {
                        if (profile.recommended) stringResource(R.string.hagezi_recommended_suffix, it) else it
                    },
                    description = stringResource(profile.descriptionRes),
                    icon = IconType.VectorIcon(Icons.Rounded.Shield),
                    actionType = SettingType.RADIOBUTTON,
                    isUsable = !busy,
                    variable = activeProfile?.id == profile.id,
                    onSwitchEnabled = { selected ->
                        if (selected && !busy) hageziViewModel.applyProfile(profile)
                    }
                )
            }
            SettingsBox(
                title = stringResource(R.string.hagezi_stevenblack_alternative_title),
                description = stringResource(R.string.hagezi_stevenblack_alternative_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Shield),
                actionType = SettingType.SWITCH,
                isUsable = !busy,
                variable = enabledUrls.contains(StevenBlackCatalog.UNIFIED.url),
                onSwitchEnabled = { enabled ->
                    if (!busy) hageziViewModel.toggle(StevenBlackCatalog.UNIFIED, enabled)
                }
            )
        }

        item {
            ToolSectionHeader(
                title = stringResource(R.string.hagezi_compatibility_title),
                subtitle = stringResource(R.string.hagezi_compatibility_desc)
            )
        }

        settingsContainer {
            SettingsBox(
                title = stringResource(R.string.hagezi_compatibility_allowlist_title),
                description = stringResource(R.string.hagezi_compatibility_allowlist_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Lock),
                actionType = SettingType.SWITCH,
                isUsable = !busy,
                variable = compatibilityEnabled,
                onSwitchEnabled = { enabled ->
                    if (!busy) hageziViewModel.toggleCompatibilityAllowlist(enabled)
                }
            )
        }

        item {
            ToolSectionHeader(
                title = stringResource(R.string.hagezi_maintenance_title),
                subtitle = stringResource(R.string.hagezi_maintenance_desc)
            )
        }

        settingsContainer {
            SettingsBox(
                title = stringResource(R.string.hagezi_disable_all_title),
                description = stringResource(R.string.hagezi_disable_all_desc),
                icon = IconType.VectorIcon(Icons.Rounded.DeleteSweep),
                actionType = SettingType.CUSTOM,
                isUsable = !busy,
                customAction = { onExit ->
                    hageziViewModel.disableAll()
                    onExit()
                }
            )
        }

        item {
            ToolSectionHeader(
                title = stringResource(R.string.hagezi_optional_lists_title),
                subtitle = stringResource(R.string.hagezi_optional_lists_desc)
            )
        }

        // Toggle and lists live in the same item: the lists expand right under
        // the switch instead of being appended far below the visible area.
        item {
            Column {
                Column(modifier = Modifier.clip(RoundedCornerShape(32.dp))) {
                    SettingsBox(
                        title = stringResource(R.string.hagezi_show_lists_title),
                        description = stringResource(R.string.hagezi_show_lists_desc),
                        icon = IconType.VectorIcon(Icons.AutoMirrored.Rounded.List),
                        actionType = SettingType.SWITCH,
                        variable = showOptionalLists,
                        onSwitchEnabled = { showOptionalLists = it }
                    )
                }
                AnimatedVisibility(
                    visible = showOptionalLists,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        HageziCatalog.all.groupBy { it.category }.forEach { (category, lists) ->
                            CatalogueSection(
                                title = stringResource(category.titleRes),
                                subtitle = stringResource(category.descriptionRes),
                                lists = lists,
                                icon = iconFor(category),
                                enabledUrls = enabledUrls,
                                busy = busy,
                                onToggle = { list, enabled -> hageziViewModel.toggle(list, enabled) }
                            )
                        }
                        StevenBlackCatalog.all.groupBy { it.category }.forEach { (category, lists) ->
                            CatalogueSection(
                                title = stringResource(category.titleRes),
                                subtitle = stringResource(category.descriptionRes),
                                lists = lists,
                                icon = iconFor(category),
                                enabledUrls = enabledUrls,
                                busy = busy,
                                onToggle = { list, enabled -> hageziViewModel.toggle(list, enabled) }
                            )
                        }
                    }
                }
            }
        }
    }

    DownloadDialog(
        title = HageziCatalog.profiles.firstOrNull { it.name == currentListName }
            ?.let { stringResource(it.nameRes) }
            ?: currentListName
            ?: stringResource(R.string.hagezi_downloading_lists),
        isVisible = showDownloadDialog,
        downloadState = downloadState ?: com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.components.DownloadState.Downloading,
        progress = progress,
        stage = stage,
        onDismiss = { hageziViewModel.dismissDownloadDialog() },
        onRetry = {
            val profile = activeProfile
            if (profile != null) {
                hageziViewModel.applyProfile(profile)
            } else {
                hageziViewModel.refreshEnabled()
                hageziViewModel.dismissDownloadDialog()
            }
        }
    )
}

@Composable
private fun CatalogueSection(
    title: String,
    subtitle: String,
    lists: List<BlocklistSource>,
    icon: ImageVector,
    enabledUrls: Set<String>,
    busy: Boolean,
    onToggle: (BlocklistSource, Boolean) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolSectionHeader(title = title, subtitle = subtitle)
        Column(modifier = Modifier.clip(RoundedCornerShape(32.dp))) {
            lists.forEach { list ->
                val covering = BlocklistCoverage.coveringName(list.url, enabledUrls)
                val covered = covering != null
                val baseDescription = list.descriptionRes?.let { stringResource(it) } ?: list.description
                SettingsBox(
                    title = if (list.recommended) stringResource(R.string.hagezi_recommended_suffix, list.name) else list.name,
                    description = if (covered) {
                        baseDescription + "\n" + stringResource(R.string.blocklists_included_in, covering)
                    } else {
                        baseDescription
                    },
                    icon = IconType.VectorIcon(icon),
                    actionType = SettingType.SWITCH,
                    isUsable = !busy && !covered,
                    variable = enabledUrls.contains(list.url) || covered,
                    onSwitchEnabled = { enabled -> if (!busy && !covered) onToggle(list, enabled) },
                    onNotUsableClicked = {
                        if (covered) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.blocklists_included_in, covering),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ToolSectionHeader(title: String, subtitle: String) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun iconFor(category: HageziCategory) = when (category) {
    HageziCategory.CORE -> Icons.Rounded.Shield
    HageziCategory.SECURITY -> Icons.Rounded.Security
    HageziCategory.PRIVACY -> Icons.Rounded.Lock
    HageziCategory.FAMILY -> Icons.Rounded.FamilyRestroom
    HageziCategory.NETWORK -> Icons.Rounded.Public
}

private fun iconFor(category: StevenBlackCategory) = when (category) {
    StevenBlackCategory.UNIFIED -> Icons.Rounded.Shield
    StevenBlackCategory.CONTENT -> Icons.Rounded.Security
}
