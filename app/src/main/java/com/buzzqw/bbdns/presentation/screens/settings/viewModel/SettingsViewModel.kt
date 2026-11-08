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
package com.buzzqw.bbdns.presentation.screens.settings.viewModel

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buzzqw.bbdns.BuildConfig
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.core.utils.DeviceDisplay
import com.buzzqw.bbdns.domain.model.Settings
import com.buzzqw.bbdns.domain.usecase.preferences.PreferencesUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import com.buzzqw.bbdns.service.utils.manager.FirewallManager
import java.security.MessageDigest
import javax.inject.Inject
import java.security.NoSuchAlgorithmException

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceUseCases: PreferencesUseCases,
    private val firewallManager: FirewallManager
) : ViewModel() {

    private val _settings = mutableStateOf(Settings())
    val settings: State<Settings> = _settings

    var defaultRoute: String? = null
    
    val version: String = BuildConfig.VERSION_NAME
    val build: String = BuildConfig.BUILD_TYPE
    
    // Callback for when app filtering settings change
    var onAppFilteringSettingsChanged: (() -> Unit)? = null

    init {
        runBlocking {
            loadSettings()
        }
    }

    fun loadDefaultRoute() {
        defaultRoute = _settings.value.defaultRoute
    }

    private suspend fun loadSettings() {
        val result = preferenceUseCases.loadSettings.execute()
        result.fold(
            ifSuccess = { loadedSettings ->
                val migrationPrefs = context.getSharedPreferences("bbdns_migrations", Context.MODE_PRIVATE)
                val amoledDefaultApplied = migrationPrefs.getBoolean("amoled_default_applied", false)
                val enableAmoled = !amoledDefaultApplied &&
                    !loadedSettings.amoledTheme &&
                    DeviceDisplay.isProbablyAmoled()
                val effective = if (enableAmoled) {
                    loadedSettings.copy(amoledTheme = true)
                } else {
                    loadedSettings
                }
                _settings.value = effective
                defaultRoute = effective.defaultRoute
                if (!amoledDefaultApplied) {
                    migrationPrefs.edit().putBoolean("amoled_default_applied", true).apply()
                }
                if (enableAmoled) {
                    persist(effective)
                }
            }
        )
    }

    fun updateDefaultRoute(route: String) {
        _settings.value = _settings.value.copy(defaultRoute = route)
        viewModelScope.launch {
            persist(_settings.value)
        }
    }

    /**
     * Save the UI snapshot without clobbering the fields that are owned by
     * background components (VPN service, list seeding): they are re-read from
     * disk right before writing.
     */
    private suspend fun persist(newSettings: Settings) =
        preferenceUseCases.saveSettings.execute(
            preferenceUseCases.loadSettings.execute().fold(
                ifSuccess = { onDisk ->
                    newSettings.copy(
                        protectionEnabled = onDisk.protectionEnabled,
                        defaultBlocklistsSeeded = onDisk.defaultBlocklistsSeeded
                    )
                },
                ifFailure = { newSettings }
            )
        )

    fun update(newSettings: Settings, onSuccess: (() -> Unit)? = null) {
        val oldSettings = _settings.value
        if (oldSettings == newSettings) {
            onSuccess?.invoke()
            return
        }
        _settings.value = newSettings.copy()

        // Check if app filtering settings changed and trigger reload IMMEDIATELY
        if (oldSettings.showSystemPackages != newSettings.showSystemPackages ||
            oldSettings.showOfflinePackages != newSettings.showOfflinePackages) {
            Logger.info("SettingsViewModel: App filtering settings changed, triggering immediate app reload")
            onAppFilteringSettingsChanged?.invoke()
        }

        viewModelScope.launch {
            try {
                persist(newSettings).fold(
                    ifSuccess = {
                        // Check if blockPort80 setting changed
                        if (oldSettings.blockPort80 != newSettings.blockPort80) {
                            firewallManager.updateHttpSettings()
                        }

                        onSuccess?.invoke()
                    },
                    ifFailure = { error ->
                        Logger.error("Failed to save settings: ${error.message}", error)
                    }
                )
            } catch (e: CancellationException) {
                // Don't log cancellation as error - it's expected behavior
                Logger.debug("Settings update cancelled")
                throw e // Re-throw to properly cancel the coroutine
            } catch (e: Exception) {
                Logger.error("Error updating settings: ${e.message}", e)
            }
        }
    }


    fun getAppSignature(): String? {
        return try {
            val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            val signatures = packageInfo.signatures

            if (signatures != null) {
                for (signature in signatures) {
                    val cert = signature.toByteArray()
                    val md = MessageDigest.getInstance("SHA-256")
                    val digest = md.digest(cert)
                    val hexString = digest.joinToString("") { "%02x".format(it) }
                    return hexString
                }
            }
        null
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            null
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            null
        }
    }
}
