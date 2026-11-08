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

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/**
 * Per-app language picker. The options come from the generated
 * `locales_config.xml`, which is built from the `values-<locale>` resource
 * folders: shipping a translated `strings.xml` is enough to make a language
 * selectable here and in Android's system settings.
 *
 * A null [LanguageOption.tag] means "follow the system": the stored locale
 * list is empty and resources use the device language.
 */
private data class LanguageOption(
    val tag: String?,
    val label: String,
)

@Composable
fun LanguageScreen(
    navController: NavController,
    settings: SettingsViewModel,
) {
    val context = LocalContext.current
    val currentTag = AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag()
    val displayLocale = context.resources.configuration.locales[0]
    val systemLabel = stringResource(R.string.settings_language_option_system)

    val languageOptions = remember(displayLocale, systemLabel) {
        buildList {
            add(LanguageOption(null, systemLabel))
            availableLocaleQualifiers(context).forEach { qualifier ->
                val tag = qualifierToTag(qualifier)
                val label = Locale.forLanguageTag(tag)
                    .getDisplayName(displayLocale)
                    .replaceFirstChar { char ->
                        if (char.isLowerCase()) char.titlecase(displayLocale) else char.toString()
                    }
                add(LanguageOption(tag, label))
            }
        }
    }

    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.settings_language),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        settingsContainer(titleRes = R.string.settings_language) {
            languageOptions.forEach { option ->
                SettingsBox(
                    title = option.label,
                    icon = IconType.VectorIcon(Icons.Rounded.Language),
                    actionType = SettingType.RADIOBUTTON,
                    variable = option.tag == currentTag,
                    onSwitchEnabled = { applyLanguage(option.tag) }
                )
            }
        }
    }
}

/** Locale qualifiers declared in the generated `locales_config.xml`. */
private fun availableLocaleQualifiers(context: Context): List<String> {
    val parser = context.resources.getXml(R.xml.locales_config)
    val qualifiers = mutableListOf<String>()
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
        if (event == XmlPullParser.START_TAG && parser.name == "locale") {
            // The attribute is android:name, so it lives in the Android namespace.
            parser.getAttributeValue(ANDROID_NAMESPACE, "name")?.let(qualifiers::add)
        }
        event = parser.next()
    }
    return qualifiers
}

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

/** Android resource qualifier (`zh-rCN`) to BCP 47 tag (`zh-CN`). */
private fun qualifierToTag(qualifier: String): String = qualifier.replace("-r", "-")

private fun applyLanguage(tag: String?) {
    AppCompatDelegate.setApplicationLocales(
        if (tag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
    )
}
