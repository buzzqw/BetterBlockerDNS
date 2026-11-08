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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ContactSupport
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.constants.AppConstants
import com.buzzqw.bbdns.core.utils.constants.ProjectConstants
import com.buzzqw.bbdns.core.utils.extensions.safeNavigate
import com.buzzqw.bbdns.presentation.components.material.MaterialTextField
import com.buzzqw.bbdns.presentation.navigation.routes.HomeRoutes
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingDialog
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsScaffold
import com.buzzqw.bbdns.presentation.screens.settings.components.settingsContainer
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.about.components.LogoWithBlob
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.about.components.SettingsVerification
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Camera
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@Composable
fun AboutScreen(
    navController: NavController,
    settings: SettingsViewModel,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    
    SettingsScaffold(
        settings = settings,
        title = stringResource(id = R.string.about_title),
        onBackNavClicked = { navController.navigateUp() }
    ) {
        item {
            LogoWithBlob {
                navController.safeNavigate(HomeRoutes.Debug.route)
            }
        }
        item {
            SettingsVerification(
                isValid = settings.getAppSignature() in ProjectConstants.SHA_256_SIGNING,
                title = stringResource(id = R.string.about_verified_build),
                description = stringResource(id = R.string.about_maintained_by) + " " + ProjectConstants.DEVELOPER
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.about_translator),
                description = stringResource(id = R.string.about_your_name),
                actionType = SettingType.TEXT,
                icon = IconType.VectorIcon(Icons.Rounded.Translate),
            )
            SettingsBox(
                title = stringResource(id = R.string.details_version),
                description = settings.version,
                icon = IconType.VectorIcon(Icons.Rounded.Info),
                actionType = SettingType.TEXT,
            )
            SettingsBox(
                title = stringResource(id = R.string.about_build_type),
                description = settings.build,
                icon = IconType.VectorIcon(Icons.Rounded.Build),
                actionType = SettingType.TEXT,
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.about_latest_release),
                icon = IconType.VectorIcon(Icons.Rounded.Verified),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.PROJECT_DOWNLOADS) },
            )
            SettingsBox(
                title = stringResource(id = R.string.about_source_code),
                icon = IconType.VectorIcon(Icons.Rounded.Download),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.PROJECT_SOURCE_CODE) }
            )
            SettingsBox(
                title = stringResource(id = R.string.settings_license),
                description = stringResource(id = R.string.license_description),
                icon = IconType.VectorIcon(Icons.Rounded.Gavel),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.LICENSE_URL) }
            )
            SettingsBox(
                title = stringResource(id = R.string.about_hagezi_title),
                description = stringResource(id = R.string.about_hagezi_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Verified),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.HAGEZI_REPO) }
            )
            SettingsBox(
                title = stringResource(id = R.string.about_stevenblack_title),
                description = stringResource(id = R.string.about_stevenblack_desc),
                icon = IconType.VectorIcon(Icons.Rounded.Verified),
                actionType = SettingType.LINK,
                onLinkClicked = { uriHandler.openUri(ProjectConstants.STEVENBLACK_REPO) }
            )
        }
        settingsContainer {
            SettingsBox(
                title = stringResource(id = R.string.about_email),
                icon = IconType.VectorIcon(Icons.Rounded.Email),
                clipboardText = ProjectConstants.SUPPORT_MAIL,
                actionType = SettingType.CLIPBOARD,
            )
            SettingsBox(
                title = stringResource(id = R.string.about_feature),
                icon = IconType.VectorIcon(Icons.Rounded.BugReport),
                onLinkClicked = { uriHandler.openUri(ProjectConstants.GITHUB_FEATURE_REQUEST) },
                actionType = SettingType.LINK,
            )
        }
    }
}

