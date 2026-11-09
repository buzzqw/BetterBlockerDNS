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

package com.buzzqw.bbdns.presentation.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.Typography
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel


/**
 * Pure black background and near-black elevated surfaces for AMOLED panels.
 * A small step between container levels keeps cards distinguishable.
 */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF050806),
    surfaceContainer = Color(0xFF0A0E0B),
    surfaceContainerHigh = Color(0xFF0F1411),
    surfaceContainerHighest = Color(0xFF141A16),
)

private fun getColorScheme(context: Context, isDarkTheme: Boolean, isDynamicTheme: Boolean, isAmoledTheme: Boolean, keyColor: Color, useSystemDynamic: Boolean): ColorScheme {
    if (isDynamicTheme) {
        // Dynamic Colors ON
        if (useSystemDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use system Material You colors (from wallpaper)
            if (isDarkTheme) {
                val darkDynamic = getDarkDynamicColorScheme(context)
                return if (isAmoledTheme) darkDynamic.amoled() else darkDynamic
            } else {
                return getLightDynamicColorScheme(context)
            }
        } else {
            // Use custom accent color generation
            if (isDarkTheme) {
                val darkPalette = generatePalette(keyColor, true)
                return if (isAmoledTheme) darkPalette.amoled() else darkPalette
            } else {
                return generatePalette(keyColor, false)
            }
        }
    } else {
        // Dynamic Colors OFF → Use original static color schemes
        if (isDarkTheme) {
            return if (isAmoledTheme) darkScheme.amoled() else darkScheme
        } else {
            return lightScheme
        }
    }
}

@Composable
fun BbdnsTheme(
    settingsModel: SettingsViewModel,
    content: @Composable () -> Unit
) {

    val context = LocalContext.current
    val activity = LocalView.current.context as Activity

    val settings = settingsModel.settings.value
    val systemDark = isSystemInDarkTheme()
    val wantDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Sync the automatic theme outside composition and only when something
    // actually changed: writing settings on every recomposition overwrote
    // values saved by the VPN service in the meantime.
    LaunchedEffect(settings.automaticTheme, systemDark) {
        val current = settingsModel.settings.value
        if (current.automaticTheme &&
            (current.darkTheme != systemDark || current.dynamicTheme != wantDynamic)
        ) {
            settingsModel.update(current.copy(darkTheme = systemDark, dynamicTheme = wantDynamic))
        }
    }

    // While the automatic theme is on, follow the system immediately.
    val isDark = if (settings.automaticTheme) systemDark else settings.darkTheme
    val isDynamic = if (settings.automaticTheme) wantDynamic else settings.dynamicTheme

    SideEffect {
        if (settings.screenProtection) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = !isDark
    }

    // Migrate the old grey default to the BBDNS brand green.
    val storedColor = settings.customColor
    val effectiveColor = if (storedColor == -7896468) -14975141 else storedColor
    val keyColor = Color(effectiveColor)
    // BBDNS always uses its own brand palette: wallpaper colours made the
    // default theme look pink/orange instead of the brand green.
    val useSystemDynamic = false
    MaterialTheme(
        colorScheme = getColorScheme(context, isDark, isDynamic, settings.amoledTheme, keyColor, useSystemDynamic),
        typography = Typography(),
        content = content
    )
}