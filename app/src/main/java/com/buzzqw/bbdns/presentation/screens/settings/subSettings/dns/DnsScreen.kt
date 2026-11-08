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

import CustomDnsDialog
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.extensions.openPrivateDnsSettings
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.ListDialog
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.components.DohProviderDialog
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import com.buzzqw.bbdns.service.vpn.network.util.DohProviders
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants

/**
 * DNS resolver: which upstream server answers the allowed queries (preset or
 * custom), the optional DNS over HTTPS layer and a shortcut to Android's
 * Private DNS page. Blocklists live in [BlocklistsScreen].
 */
@Composable
fun DnsScreen(
    navController: NavController,
    settings: SettingsViewModel,
) {
    val context = LocalContext.current
    var showDohDialog by remember { mutableStateOf(false) }
    // Mirror the DoH switch locally so the row updates immediately and never
    // keeps showing "active" after the user turns it off.
    var dohEnabled by remember(settings.settings.value.dohEnabled) {
        mutableStateOf(settings.settings.value.dohEnabled)
    }

    val currentResolver = NetworkConstants.DNS_SERVERS
        .firstOrNull { it.ipv4Primary == settings.settings.value.dnsServer1 }
        ?.name

    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings_dns_resolver),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer(titleRes = R.string.dns_resolver_section_server) {
            SettingsBox(
                title = stringResource(R.string.dns_resolver_server_title),
                description = currentResolver
                    ?: stringResource(R.string.dns_resolver_server_custom, settings.settings.value.dnsServer1),
                icon = IconType.VectorIcon(Icons.Rounded.Dns),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    OnDNSClicked(settings, onExit)
                }
            )
        }
        settingsContainer(titleRes = R.string.dns_resolver_section_encrypted) {
            SettingsBox(
                title = stringResource(R.string.dns_resolver_doh_title),
                description = if (dohEnabled) {
                    stringResource(R.string.dns_resolver_doh_on_desc, DohProviders.nameOrNull(settings.settings.value.dohUrl) ?: stringResource(R.string.doh_custom))
                } else {
                    stringResource(R.string.dns_resolver_doh_off_desc)
                },
                icon = IconType.VectorIcon(Icons.Rounded.Lock),
                actionType = SettingType.SWITCH,
                variable = dohEnabled,
                onSwitchEnabled = { enabled ->
                    dohEnabled = enabled
                    settings.update(settings.settings.value.copy(dohEnabled = enabled))
                }
            )

            if (dohEnabled) {
                SettingsBox(
                    title = stringResource(R.string.dns_resolver_doh_provider_title),
                    description = "${DohProviders.nameOrNull(settings.settings.value.dohUrl) ?: stringResource(R.string.doh_custom)}\n${settings.settings.value.dohUrl}",
                    icon = IconType.VectorIcon(Icons.Rounded.Security),
                    actionType = SettingType.CUSTOM,
                    customAction = { onExit ->
                        showDohDialog = true
                        onExit()
                    }
                )
            }
        }
        settingsContainer(titleRes = R.string.dns_resolver_section_android) {
            SettingsBox(
                title = stringResource(R.string.dns_resolver_private_dns_title),
                description = if (dohEnabled) {
                    stringResource(R.string.dns_resolver_private_dns_doh_desc)
                } else {
                    stringResource(R.string.dns_resolver_private_dns_desc)
                },
                icon = IconType.VectorIcon(Icons.Rounded.Warning),
                actionType = SettingType.CUSTOM,
                onCustomClick = {
                    if (!context.openPrivateDnsSettings()) {
                        Toast.makeText(
                            context,
                            R.string.dns_resolver_private_dns_unavailable,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }
    if (showDohDialog) {
        DohProviderDialog(
            currentUrl = settings.settings.value.dohUrl,
            onExit = { showDohDialog = false },
            onSelected = { url ->
                settings.update(settings.settings.value.copy(dohUrl = url))
            }
        )
    }
}

@Composable
private fun OnDNSClicked(settings: SettingsViewModel, onExit: () -> Unit) {
    val list = NetworkConstants.DNS_SERVERS
    var useCustom = true

    list.forEach { dns ->
        if (
            settings.settings.value.dnsServer1 == dns.ipv4Primary ||
            settings.settings.value.dnsServer1 == dns.ipv4Primary &&
            settings.settings.value.dnsServer2 == dns.ipv4Primary ||
            settings.settings.value.dnsServer2 == dns.ipv4Primary
        ) {
            useCustom = false
        }
    }

    ListDialog(
        text = stringResource(R.string.dns_resolver_server_title),
        list = list,
        onExit = onExit,
        extractDisplayData = { it },
        customItem = {
            SettingsBox(
                size = 8.dp,
                title = stringResource(R.string.dns_custom),
                description = stringResource(R.string.dns_set_custom),
                actionType = SettingType.CUSTOM,
                customAction = { onExit ->
                    CustomDnsDialog(
                        onExit = onExit,
                        dns1v4Key = if (useCustom) settings.settings.value.dnsServer1 else null,
                        dns2v4Key = if (useCustom) settings.settings.value.dnsServer2 else null,
                        dns1v6Key = if (useCustom) settings.settings.value.dnsServer1v6 else null,
                        dns2v6Key = if (useCustom) settings.settings.value.dnsServer2v6 else null,
                        onDone = { dns1v4, dns2v4, dns1v6, dns2v6 ->
                            settings.update(settings.settings.value.copy(
                                dnsServer1 = dns1v4,
                                dnsServer2 = dns2v4,
                                dnsServer1v6 = dns1v6,
                                dnsServer2v6 = dns2v6
                            ))
                        }
                    )
                }
            )
        },
        setting = { displayData ->
            SettingsBox(
                size = 8.dp,
                title = displayData.name,
                description = displayData.ipv4Primary,
                actionType = SettingType.RADIOBUTTON,
                variable = settings.settings.value.dnsServer1 == displayData.ipv4Primary,
                onSwitchEnabled = {
                    settings.update(settings.settings.value.copy(
                        dnsServer1 = displayData.ipv4Primary,
                        dnsServer2 = displayData.ipv4Secondary,
                        dnsServer1v6 = displayData.ipv6Primary,
                        dnsServer2v6 = displayData.ipv6Secondary
                    ))
                }
            )
        }
    )
}
