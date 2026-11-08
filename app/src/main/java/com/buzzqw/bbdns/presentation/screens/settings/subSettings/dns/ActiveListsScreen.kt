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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.NumberFormatter
import com.buzzqw.bbdns.presentation.config
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsSectionHeader
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.BlocklistNames
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.CompatibilityAllowlist
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.viewModel.BlockListViewModel
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel

/**
 * Dedicated "Blocked domains" page: every enabled blocklist and how many hosts
 * it contributes, reached from the home counter and from the Blocklists row.
 */
@Composable
fun ActiveListsScreen(
    navController: NavController,
    settings: SettingsViewModel,
    blockListViewModel: BlockListViewModel = hiltViewModel(),
) {
    val listStats by RuleDatabase.listStats.collectAsState()
    val total = listStats.sumOf { it.entries.toLong() }
    val compatibilityEnabled = config.hosts.exceptions.any {
        it.title == CompatibilityAllowlist.EXCEPTION_TITLE && it.state == HostState.ALLOW
    }

    LaunchedEffect(Unit) {
        blockListViewModel.ensureDomainsLoaded()
    }

    SettingsScaffold(
        settings = settings,
        title = stringResource(R.string.active_lists_title),
        onBackNavClicked = { navController.navigateUp() },
    ) {
        item {
            Text(
                text = stringResource(R.string.active_lists_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }

        settingsContainer {
            SettingsBox(
                title = stringResource(R.string.active_lists_title),
                description = if (listStats.isEmpty()) {
                    stringResource(R.string.active_lists_empty)
                } else {
                    stringResource(
                        R.string.active_lists_total,
                        NumberFormatter.formatCount(total),
                        listStats.size,
                    )
                },
                icon = IconType.VectorIcon(Icons.Rounded.Numbers),
                actionType = SettingType.TEXT,
            )
            SettingsBox(
                title = stringResource(
                    if (compatibilityEnabled) {
                        R.string.active_lists_compatibility_on
                    } else {
                        R.string.active_lists_compatibility_off
                    }
                ),
                description = stringResource(R.string.blocklists_compatibility_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Shield),
                actionType = SettingType.TEXT,
            )
        }

        if (listStats.isNotEmpty()) {
            item {
                SettingsSectionHeader(stringResource(R.string.blocklists_active_title))
            }
            settingsContainer {
                listStats.sortedByDescending { it.entries }.forEach { stat ->
                    SettingsBox(
                        title = BlocklistNames.displayName(stat.title, stat.data),
                        description = stringResource(
                            R.string.active_lists_entries,
                            NumberFormatter.formatCount(stat.entries.toLong()),
                        ),
                        icon = IconType.VectorIcon(
                            if (stat.state == HostState.ALLOW) {
                                Icons.Rounded.Shield
                            } else {
                                Icons.Rounded.List
                            }
                        ),
                        actionType = SettingType.TEXT,
                    )
                }
            }
        }
    }
}
