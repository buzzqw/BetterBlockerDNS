/*
 * Copyright (C) 2025-2026 Vexzure
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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.about.debug

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel

@Composable
fun DebugScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel,
) {
    SettingsScaffold(
        settings = settingsViewModel,
        title = stringResource(R.string.debug_title),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
            SettingsBox(
                title = stringResource(R.string.debug_protection_mode),
                description = stringResource(R.string.debug_protection_mode_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Security),
                actionType = SettingType.TEXT,
            )
            SettingsBox(
                title = stringResource(R.string.debug_build),
                description = settingsViewModel.build,
                icon = IconType.VectorIcon(Icons.Rounded.Info),
                actionType = SettingType.TEXT,
            )
        }
    }
}
