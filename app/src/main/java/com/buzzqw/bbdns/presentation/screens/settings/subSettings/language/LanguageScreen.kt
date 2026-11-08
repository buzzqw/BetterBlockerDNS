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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.language

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel

/**
 * Per-app language picker. [LanguageOption.tag] is null for "follow the system":
 * in that case the stored locale list is empty and resources use the device
 * language.
 */
private data class LanguageOption(
    val tag: String?,
    @StringRes val labelRes: Int,
)

private val languageOptions = listOf(
    LanguageOption(null, R.string.settings_language_option_system),
    LanguageOption("en", R.string.language_english),
    LanguageOption("it", R.string.language_italian),
    LanguageOption("de", R.string.language_german),
    LanguageOption("fr", R.string.language_french),
    LanguageOption("es", R.string.language_spanish),
)

@Composable
fun LanguageScreen(
    navController: NavController,
    settings: SettingsViewModel,
) {
    val currentTag = AppCompatDelegate.getApplicationLocales().get(0)?.language

    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings_language),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer(titleRes = R.string.settings_language) {
            languageOptions.forEach { option ->
                SettingsBox(
                    title = stringResource(option.labelRes),
                    icon = IconType.VectorIcon(Icons.Rounded.Language),
                    actionType = SettingType.RADIOBUTTON,
                    variable = option.tag == currentTag,
                    onSwitchEnabled = { applyLanguage(option.tag) }
                )
            }
        }
    }
}

private fun applyLanguage(tag: String?) {
    AppCompatDelegate.setApplicationLocales(
        if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
    )
}
