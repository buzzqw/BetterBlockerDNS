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

package com.buzzqw.bbdns.presentation.screens.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RuleFolder
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.extensions.safeNavigate
import com.buzzqw.bbdns.presentation.navigation.routes.SettingRoutes
import com.buzzqw.bbdns.presentation.screens.settings.components.SectionBlock
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingSection
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import com.buzzqw.bbdns.service.vpn.network.util.DohProviders
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants

/**
 * Settings entry point, ordered by importance: protection (blocklists and DNS
 * resolver) first, then network, behaviour, privacy, appearance and about.
 */
@Composable
fun SettingsScreen(
    navController: NavController,
    settings: SettingsViewModel,
) {
    val currentTag = AppCompatDelegate.getApplicationLocales().get(0)?.language
    val currentLanguage = when (currentTag) {
        "en" -> stringResource(id = R.string.language_english)
        "it" -> stringResource(id = R.string.language_italian)
        "de" -> stringResource(id = R.string.language_german)
        "fr" -> stringResource(id = R.string.language_french)
        "es" -> stringResource(id = R.string.language_spanish)
        else -> stringResource(id = R.string.settings_language_option_system)
    }

    val settingsValue = settings.settings.value
    val currentResolver = NetworkConstants.DNS_SERVERS
        .firstOrNull { it.ipv4Primary == settingsValue.dnsServer1 }
        ?.name
        ?: settingsValue.dnsServer1
    val activeDns = if (settingsValue.dohEnabled) {
        stringResource(
            id = R.string.settings_dns_resolver_option_doh_active,
            DohProviders.nameOrNull(settingsValue.dohUrl) ?: stringResource(R.string.doh_custom),
        )
    } else {
        stringResource(id = R.string.settings_dns_resolver_option_active, currentResolver)
    }

    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.common_settings),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        item {
            SectionBlock(
                title = stringResource(id = R.string.settings_section_protection),
                sections = listOf(
                    SettingSection(
                        title = stringResource(id = R.string.settings_blocklists),
                        features = listOf(
                            stringResource(id = R.string.settings_blocklists_option_presets),
                            stringResource(id = R.string.settings_blocklists_option_sources),
                            stringResource(id = R.string.settings_blocklists_option_rules),
                            stringResource(id = R.string.settings_dns_option_updates)
                        ),
                        icon = Icons.Rounded.Shield,
                        onClick = { navController.safeNavigate(SettingRoutes.Blocklists.route) }
                    ),
                    SettingSection(
                        title = stringResource(id = R.string.settings_dns_resolver),
                        features = buildList {
                            add(activeDns)
                            add(stringResource(id = R.string.settings_dns_resolver_option_server))
                            add(
                                if (settings.settings.value.dohEnabled) {
                                    stringResource(id = R.string.settings_dns_resolver_option_doh_on)
                                } else {
                                    stringResource(id = R.string.settings_dns_resolver_option_doh)
                                }
                            )
                            add(stringResource(id = R.string.settings_dns_resolver_option_private_dns))
                        },
                        icon = Icons.Rounded.Dns,
                        onClick = { navController.safeNavigate(SettingRoutes.Dns.route) }
                    )
                )
            )
        }
        item {
            SectionBlock(
                title = stringResource(id = R.string.settings_section_network),
                sections = listOf(
                    SettingSection(
                        title = stringResource(id = R.string.settings_network),
                        features = listOf(
                            stringResource(id = R.string.settings_network_option_lockdown),
                            stringResource(id = R.string.network_kill_switch)
                        ),
                        icon = Icons.Rounded.Wifi,
                        onClick = { navController.safeNavigate(SettingRoutes.Network.route) }
                    )
                )
            )
        }
        item {
            SectionBlock(
                title = stringResource(id = R.string.settings_section_general),
                sections = listOf(
                    SettingSection(
                        title = stringResource(id = R.string.settings_behavior),
                        features = listOf(
                            stringResource(id = R.string.common_logs),
                            stringResource(id = R.string.settings_behavior_option_system),
                        ),
                        icon = Icons.Rounded.RuleFolder,
                        onClick = { navController.safeNavigate(SettingRoutes.Behavior.route) }
                    ),
                    SettingSection(
                        title = stringResource(id = R.string.settings_notifications),
                        features = listOf(
                            stringResource(id = R.string.settings_notifications_install_alerts),
                            stringResource(id = R.string.settings_notifications_activity),
                        ),
                        icon = Icons.Rounded.Notifications,
                        onClick = { navController.safeNavigate(SettingRoutes.Notifications.route) }
                    )
                )
            )
        }
        item {
            SectionBlock(
                title = stringResource(id = R.string.settings_section_privacy),
                sections = listOf(
                    SettingSection(
                        title = stringResource(id = R.string.settings_privacy),
                        features = listOf(
                            stringResource(id = R.string.settings_privacy_option_lock),
                            stringResource(id = R.string.settings_privacy_option_hide),
                            stringResource(id = R.string.settings_privacy_option_backup),
                        ),
                        icon = ImageVector.vectorResource(id = R.drawable.incognito),
                        onClick = { navController.safeNavigate(SettingRoutes.Privacy.route) }
                    )
                )
            )
        }
        item {
            SectionBlock(
                title = stringResource(id = R.string.settings_section_appearance),
                sections = listOf(
                    SettingSection(
                        title = stringResource(id = R.string.settings_colors),
                        features = listOf(
                            stringResource(id = R.string.settings_colors_option_theme),
                            stringResource(id = R.string.colors_dynamic_icons),
                        ),
                        icon = Icons.Rounded.Palette,
                        onClick = { navController.safeNavigate(SettingRoutes.Colors.route) }
                    ),
                    SettingSection(
                        title = stringResource(id = R.string.settings_language),
                        features = listOf(currentLanguage),
                        icon = Icons.Rounded.Language,
                        onClick = { navController.safeNavigate(SettingRoutes.Language.route) }
                    )
                )
            )
        }
        item {
            SectionBlock(
                title = stringResource(id = R.string.settings_section_about),
                sections = listOf(
                    SettingSection(
                        title = stringResource(id = R.string.about_title),
                        features = listOf(
                            stringResource(id = R.string.details_version),
                            stringResource(id = R.string.settings_about_option_devs),
                            stringResource(id = R.string.settings_license)
                        ),
                        icon = Icons.Rounded.Info,
                        onClick = { navController.safeNavigate(SettingRoutes.About.route) }
                    )
                )
            )
        }
    }
}
