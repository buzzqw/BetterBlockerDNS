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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.behavior

import android.annotation.SuppressLint
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.SignalWifiBad
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import androidx.compose.material.icons.rounded.Android
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.behavior.viewModel.BehaviorViewModel
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import com.buzzqw.bbdns.service.firewall.utils.FirewallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun BehaviorScreen(
    navController: NavController,
    settings: SettingsViewModel,
    behaviorViewModel: BehaviorViewModel = hiltViewModel()
) {
    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings_behavior),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        // Logs section - on top
        settingsContainer {
            val logsTitle = stringResource(id = R.string.common_logs)
            val logsDescription = stringResource(id = R.string.behavior_logs_desc)

            SettingsBox(
                title = logsTitle,
                description = logsDescription,
                icon = IconType.VectorIcon(Icons.Rounded.Code),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.logs,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(logs = it))
                    behaviorViewModel.updateLogs(it)
                }
            )
            SettingsBox(
                title = stringResource(R.string.behavior_detailed_logs),
                description = stringResource(R.string.behavior_detailed_logs_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Description),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.detailedLogs,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(detailedLogs = it))
                }
            )
        }

        // Protection method: BBDNS is VPN-only (no root, no privileged helpers)
        settingsContainer {
            SettingsBox(
                title = stringResource(R.string.behavior_protection_method),
                description = stringResource(R.string.behavior_protection_method_desc),
                icon = IconType.VectorIcon(Icons.Rounded.SignalWifiBad),
                actionType = SettingType.TEXT,
            )
        }

        // Other settings section
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.behavior_start_on_boot),
                description = stringResource(id = R.string.behavior_start_on_boot_desc),
                icon = IconType.VectorIcon(Icons.Rounded.RestartAlt),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.startOnBoot,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(startOnBoot = it))
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.behavior_show_system_apps),
                description = stringResource(id = R.string.behavior_show_system_apps_desc),
                icon = IconType.VectorIcon(Icons.Rounded.SettingsApplications),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.showSystemPackages,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(showSystemPackages = it))
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.behavior_show_offline_apps),
                description = stringResource(id = R.string.behavior_show_offline_apps_desc),
                icon = IconType.VectorIcon(Icons.Rounded.SignalWifiBad),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.showOfflinePackages,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(showOfflinePackages = it))
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.behavior_block_wifi_default),
                description = stringResource(id = R.string.behavior_block_wifi_default_desc),
                icon = IconType.VectorIcon(Icons.Rounded.WifiOff),
                actionType = SettingType.SWITCH,
                variable = !settings.settings.value.wiFiDefault,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(wiFiDefault = !it))
                    behaviorViewModel.updateApp(
                        settings.settings.value.wiFiDefault,
                        settings.settings.value.cellularDefault,
                        settings.settings.value.showSystemPackages
                    )
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.behavior_block_cellular_default),
                description = stringResource(id = R.string.behavior_block_cellular_default_desc),
                icon = IconType.VectorIcon(Icons.Rounded.SignalCellularOff),
                actionType = SettingType.SWITCH,
                variable = !settings.settings.value.cellularDefault,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(cellularDefault = !it))
                    behaviorViewModel.updateApp(
                        settings.settings.value.wiFiDefault,
                        settings.settings.value.cellularDefault,
                        settings.settings.value.showSystemPackages
                    )
                }
            )
        }
    }
}
