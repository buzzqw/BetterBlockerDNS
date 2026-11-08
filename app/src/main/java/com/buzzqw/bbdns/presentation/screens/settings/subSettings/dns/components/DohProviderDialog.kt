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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.components

import com.buzzqw.bbdns.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.buzzqw.bbdns.presentation.components.material.MaterialTextField
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.ListDialog
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingDialog
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.service.vpn.network.util.DohProvider
import com.buzzqw.bbdns.service.vpn.network.util.DohProviders

@Composable
fun DohProviderDialog(
    currentUrl: String,
    onExit: () -> Unit,
    onSelected: (String) -> Unit
) {
    var showCustom by remember { mutableStateOf(false) }

    if (showCustom) {
        CustomDohUrlDialog(
            currentUrl = currentUrl,
            onExit = { showCustom = false },
            onDone = { url ->
                onSelected(url)
                onExit()
            }
        )
        return
    }

    ListDialog(
        text = stringResource(R.string.doh_provider_title),
        list = DohProviders.all,
        onExit = onExit,
        extractDisplayData = { it },
        customItem = {
            SettingsBox(
                title = stringResource(R.string.doh_custom_url_title),
                description = stringResource(R.string.doh_custom_url_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Link),
                actionType = SettingType.CUSTOM,
                customAction = { close ->
                    showCustom = true
                    close()
                }
            )
        },
        setting = { provider: DohProvider ->
            SettingsBox(
                title = if (provider.recommended) "${provider.name} ★" else provider.name,
                description = provider.descriptionRes?.let { "${stringResource(it)}\n${provider.url}" } ?: provider.url,
                actionType = SettingType.RADIOBUTTON,
                variable = currentUrl == provider.url,
                onSwitchEnabled = { selected ->
                    if (selected) {
                        onSelected(provider.url)
                        onExit()
                    }
                }
            )
        }
    )
}

@Composable
private fun CustomDohUrlDialog(
    currentUrl: String,
    onExit: () -> Unit,
    onDone: (String) -> Unit
) {
    var value by remember {
        mutableStateOf(TextFieldValue(if (DohProviders.isCustom(currentUrl)) currentUrl else ""))
    }
    val trimmed = value.text.trim()
    val valid = trimmed.startsWith("https://") && trimmed.length > "https://".length

    SettingDialog(
        text = stringResource(R.string.doh_custom_url_title),
        onExit = onExit
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.doh_custom_url_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                MaterialTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "https://cloudflare-dns.com/dns-query",
                    singleLine = true,
                    keyboardType = KeyboardType.Uri
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onDone(trimmed) },
                enabled = valid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_save))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
