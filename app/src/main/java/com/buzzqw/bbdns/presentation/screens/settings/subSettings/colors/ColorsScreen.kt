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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.colors

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HdrAuto
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.presentation.screens.home.viewModel.HomeViewModel
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import com.buzzqw.bbdns.presentation.theme.PALETTE_COLORS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import com.buzzqw.bbdns.presentation.theme.BbdnsShapes
import kotlinx.coroutines.launch

@Composable
fun ColorsScreen(
    navController: NavController,
    settings: SettingsViewModel,
    homeViewModel: HomeViewModel,
) {
    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings_colors),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.colors_system_theme),
                description = stringResource(id = R.string.colors_system_theme_desc),
                icon = IconType.VectorIcon(Icons.Rounded.HdrAuto),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.automaticTheme,
                onSwitchEnabled = { settings.update(settings.settings.value.copy(automaticTheme = it)) }
            )
            SettingsBox(
                title = stringResource(id = R.string.colors_dark_theme),
                description = stringResource(id = R.string.colors_dark_theme_desc),
                isEnabled = !settings.settings.value.automaticTheme,
                icon = IconType.VectorIcon(Icons.Rounded.Palette),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.darkTheme,
                onSwitchEnabled = {
                    settings.update(
                        settings.settings.value.copy(
                            automaticTheme = false,
                            darkTheme = it
                        )
                    )
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.colors_dynamic),
                description = stringResource(id = R.string.colors_dynamic_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Colorize),
                isEnabled = !settings.settings.value.automaticTheme,
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.dynamicTheme,
                onSwitchEnabled = {
                    settings.update(
                        settings.settings.value.copy(
                            automaticTheme = false,
                            dynamicTheme = it
                        )
                    )
                }
            )
            SettingsBox(
                title = stringResource(id = R.string.colors_accent_color),
                description = stringResource(id = R.string.colors_accent_color_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Palette),
                isEnabled = settings.settings.value.dynamicTheme,
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    ColorSelectionDialog(
                        settings = settings,
                        onDismissRequest = onExit
                    )
                }
            )
            val iconColors =  MaterialTheme.colorScheme.primary
            SettingsBox(
                title = stringResource(id = R.string.colors_dynamic_icons),
                description = stringResource(id = R.string.colors_dynamic_icons_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Apps),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.useDynamicIcons,
                onSwitchEnabled = {
                    settings.update(
                        settings.settings.value.copy(
                            useDynamicIcons = it
                        )
                    )
                    CoroutineScope(Dispatchers.IO).launch {
                        // Get current applications and load icons with new color
                        val currentState = homeViewModel.applicationState.value
                        if (currentState is com.buzzqw.bbdns.presentation.screens.home.viewModel.ApplicationListState.Success) {
                            homeViewModel.loadIcons(
                                applications = currentState.applications,
                                settingsViewModel = settings,
                                color = iconColors
                            )
                        }
                    }
                }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.colors_amoled),
                description = stringResource(id = R.string.colors_amoled_desc),
                icon = IconType.VectorIcon(Icons.Rounded.DarkMode),
                actionType = SettingType.SWITCH,
                isEnabled = settings.settings.value.darkTheme,
                variable = settings.settings.value.amoledTheme,
                onSwitchEnabled = { settings.update(settings.settings.value.copy(amoledTheme = it)) }
            )
            SettingsBox(
                title = stringResource(id = R.string.colors_show_disable_dialog),
                description = stringResource(id =  R.string.colors_show_disable_dialog_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Security),
                actionType = SettingType.SWITCH,
                variable = settings.settings.value.showDialog,
                onSwitchEnabled = { settings.update(settings.settings.value.copy(showDialog = it)) }
            )
        }
    }
}

@Composable
private fun ColorSelectionDialog(
    settings: SettingsViewModel,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = BbdnsShapes.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Text(
                    text = stringResource(id = R.string.colors_select_accent_color),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )

                // System Colors Button
                val isUsingSystemColors = settings.settings.value.customColor == -7896468
                androidx.compose.material3.FilledTonalButton(
                    onClick = {
                        settings.update(settings.settings.value.copy(customColor = -7896468))
                        onDismissRequest()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isUsingSystemColors)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.colors_use_system_colors),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Divider with text
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    Text(
                        text = stringResource(id = R.string.colors_or_choose_custom),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    androidx.compose.material3.HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                // Color Grid
                val colorChunks = PALETTE_COLORS.chunked(4)
                colorChunks.forEach { rowColors ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowColors.forEach { color ->
                            val isSelected = settings.settings.value.customColor == color.toArgb()
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .padding(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                Color.Transparent
                                        )
                                        .padding(if (isSelected) 4.dp else 0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable {
                                                settings.update(settings.settings.value.copy(customColor = color.toArgb()))
                                                onDismissRequest()
                                            }
                                    )
                                }
                                if (isSelected) {
                                    androidx.compose.material3.Icon(
                                        imageVector = androidx.compose.material.icons.Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.common_selected),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopEnd)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}