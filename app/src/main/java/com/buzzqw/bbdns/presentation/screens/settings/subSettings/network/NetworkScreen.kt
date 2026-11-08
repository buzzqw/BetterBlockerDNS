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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.network

import android.content.Intent
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMissedOutgoing
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.filled.CallMissedOutgoing
import androidx.compose.material.icons.filled.MultipleStop
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.SignalCellularConnectedNoInternet0Bar
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.extensions.safeNavigate
import com.buzzqw.bbdns.presentation.navigation.routes.LogRoutes
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.IpDialog
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.network.viewModel.IpDialogViewModel
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import com.buzzqw.bbdns.service.utils.manager.FirewallManager
import com.buzzqw.bbdns.service.utils.manager.NetworkSpeedManager
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.AdminPanelSettings
import kotlinx.coroutines.runBlocking

@Composable
fun NetworkScreen(
    navController: NavController,
    settings: SettingsViewModel,
) {
    val context = LocalContext.current
    val networkViewModel = hiltViewModel<IpDialogViewModel>().apply {
        updateIpv4DialogText(TextFieldValue(settings.settings.value.ipv4 ?: ""))
        updateIpv6DialogText(TextFieldValue(settings.settings.value.ipv6 ?: ""))
    }

    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings_network),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
                SettingsBox(
                    title = stringResource(id = R.string.network_manage_ips),
                    description = stringResource(id = R.string.network_manage_ips_desc),
                    icon = IconType.VectorIcon(Icons.Filled.MultipleStop),
                    actionType = SettingType.CUSTOM,
                    customAction = {
                        navController.safeNavigate(LogRoutes.Ips.route)
                    },
                )
                SettingsBox(
                    title = stringResource(id = R.string.network_always_allow),
                    description = stringResource(id = R.string.network_always_allow_desc),
                    icon = IconType.VectorIcon(Icons.AutoMirrored.Filled.CallMissedOutgoing),
                    actionType = SettingType.SWITCH,
                    variable = settings.settings.value.allowLocal,
                    onSwitchEnabled = { settings.update(settings.settings.value.copy(allowLocal = it)) },
                )
            val speedMonitorTitle = stringResource(id = R.string.notification_network_speed_channel)
            val speedMonitorDescription = stringResource(id = R.string.notification_network_speed_channel_desc)
            
            SettingsBox(
                title = speedMonitorTitle,
                description = speedMonitorDescription,
                icon = IconType.VectorIcon(Icons.Filled.NetworkCheck),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.networkSpeedMonitor,
                onSwitchEnabled = { enabled ->
                    settings.update(settings.settings.value.copy(networkSpeedMonitor = enabled))
                    if (enabled) {
                        NetworkSpeedManager.start(context)
                    } else {
                        NetworkSpeedManager.stop(context)
                    }
                },
            )
        }
    }
}