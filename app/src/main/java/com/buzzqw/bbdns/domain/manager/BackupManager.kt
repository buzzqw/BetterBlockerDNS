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

package com.buzzqw.bbdns.domain.manager

import android.content.Context
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.domain.model.*
import com.buzzqw.bbdns.domain.repository.CustomDomainRepository
import com.buzzqw.bbdns.domain.usecase.application.ApplicationUseCases
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.Configuration
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostException
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.BlocklistSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val customDomainRepository: CustomDomainRepository,
    private val applicationUseCases: ApplicationUseCases
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportBackup(
        outputStream: OutputStream,
        settings: Settings,
        includeSettings: Boolean = true,
        includeDomains: Boolean = true,
        includeBlocklists: Boolean = true,
        includeApplications: Boolean = true
    ): Result<Unit> {
        return try {
            // Collect settings
            val settingsBackup = if (includeSettings) {
                SettingsBackup(
                    automaticTheme = settings.automaticTheme,
                    darkTheme = settings.darkTheme,
                    useDynamicIcons = settings.useDynamicIcons,
                    dynamicTheme = settings.dynamicTheme,
                    amoledTheme = settings.amoledTheme,
                    customColor = settings.customColor,
                    screenProtection = settings.screenProtection,
                    blockPort80 = settings.blockPort80,
                    logs = settings.logs,
                    detailedLogs = settings.detailedLogs,
                    showSystemPackages = settings.showSystemPackages,
                    showOfflinePackages = settings.showOfflinePackages,
                    wiFiDefault = settings.wiFiDefault,
                    cellularDefault = settings.cellularDefault,
                    startOnBoot = settings.startOnBoot,
                    allowLocal = settings.allowLocal,
                    blockWifiWhenScreenOff = settings.blockWifiWhenScreenOff,
                    blockCellularWhenScreenOff = settings.blockCellularWhenScreenOff,
                    permanentNotification = settings.permanentNotification,
                    networkSpeedMonitor = settings.networkSpeedMonitor,
                    sendNotificationOnInstall = settings.sendNotificationOnInstall,
                    malwareProtection = settings.malwareProtection,
                    adBlocker = settings.adBlocker,
                    trackerProtection = settings.trackerProtection,
                    autoUpdateInterval = settings.autoUpdateInterval,
                    dohEnabled = settings.dohEnabled,
                    dohUrl = settings.dohUrl
                )
            } else {
                SettingsBackup(
                    automaticTheme = true,
                    darkTheme = false,
                    useDynamicIcons = false,
                    dynamicTheme = false,
                    amoledTheme = false,
                    customColor = -7896468,
                    screenProtection = false,
                    blockPort80 = false,
                    logs = false,
                    detailedLogs = false,
                    showSystemPackages = false,
                    showOfflinePackages = false,
                    wiFiDefault = true,
                    cellularDefault = true,
                    startOnBoot = true,
                    allowLocal = true,
                    blockWifiWhenScreenOff = false,
                    blockCellularWhenScreenOff = false,
                    permanentNotification = false,
                    networkSpeedMonitor = false,
                    sendNotificationOnInstall = false,
                    malwareProtection = false,
                    adBlocker = false,
                    trackerProtection = false,
                    autoUpdateInterval = 24 * 60 * 60 * 1000L,
                    dohEnabled = false,
                    dohUrl = "https://cloudflare-dns.com/dns-query"
                )
            }

            // Collect custom domains
            val customDomains = if (includeDomains) {
                val allowlist = customDomainRepository.getAllowlistDomains().first()
                val blocklist = customDomainRepository.getBlocklistDomains().first()
                (allowlist + blocklist).map {
                    CustomDomainBackup(
                        domain = it.domain,
                        description = it.description,
                        isRegex = it.isRegex,
                        isAllowlist = it.isAllowlist,
                        isEnabled = it.isEnabled
                    )
                }
            } else {
                emptyList()
            }

            // Collect custom blocklists and allow/deny exceptions
            val configuration = if (includeBlocklists) Configuration.load() else null
            val customBlocklists = configuration?.hosts?.items?.map {
                CustomBlocklistBackup(
                    title = it.title,
                    url = it.data,
                    state = it.state.name
                )
            } ?: emptyList()
            val hostExceptions = configuration?.hosts?.exceptions?.map {
                HostExceptionBackup(
                    title = it.title,
                    hostname = it.data,
                    state = it.state.name
                )
            } ?: emptyList()

            // Collect application rules
            val applications = if (includeApplications) {
                applicationUseCases.getApplications.execute().fold(
                    ifSuccess = { apps ->
                        apps.map {
                            ApplicationBackup(
                                packageID = it.packageID,
                                internetAccess = it.internetAccess,
                                cellularAccess = it.cellularAccess,
                                bypassVpn = it.bypassVpn,
                                isPinned = it.isPinned
                            )
                        }
                    },
                    ifFailure = { emptyList() }
                )
            } else {
                emptyList()
            }

            // Create backup data
            val backupData = BackupData(
                settings = settingsBackup,
                customDomains = customDomains,
                customBlocklists = customBlocklists,
                hostExceptions = hostExceptions,
                applications = applications
            )

            // Write to output stream
            val jsonString = json.encodeToString(backupData)
            outputStream.write(jsonString.toByteArray())
            outputStream.flush()

            Logger.info("Backup exported successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.error("Failed to export backup", e)
            Result.failure(e)
        }
    }

    suspend fun importBackup(
        inputStream: InputStream,
        restoreSettings: Boolean = true,
        restoreDomains: Boolean = true,
        restoreBlocklists: Boolean = true,
        restoreApplications: Boolean = true,
        onSettingsRestored: (Settings) -> Unit
    ): Result<BackupData> {
        return try {
            val jsonString = inputStream.readBytes().decodeToString()
            val backupData = json.decodeFromString<BackupData>(jsonString)

            // Restore settings
            if (restoreSettings) {
                val s = backupData.settings
                val settings = Settings(
                    automaticTheme = s.automaticTheme,
                    darkTheme = s.darkTheme,
                    useDynamicIcons = s.useDynamicIcons,
                    dynamicTheme = s.dynamicTheme,
                    amoledTheme = s.amoledTheme,
                    customColor = s.customColor,
                    screenProtection = s.screenProtection,
                    blockPort80 = s.blockPort80,
                    logs = s.logs,
                    detailedLogs = s.detailedLogs,
                    showSystemPackages = s.showSystemPackages,
                    showOfflinePackages = s.showOfflinePackages,
                    wiFiDefault = s.wiFiDefault,
                    cellularDefault = s.cellularDefault,
                    startOnBoot = s.startOnBoot,
                    allowLocal = s.allowLocal,
                    blockWifiWhenScreenOff = s.blockWifiWhenScreenOff,
                    blockCellularWhenScreenOff = s.blockCellularWhenScreenOff,
                    permanentNotification = s.permanentNotification,
                    networkSpeedMonitor = s.networkSpeedMonitor,
                    sendNotificationOnInstall = s.sendNotificationOnInstall,
                    malwareProtection = s.malwareProtection,
                    adBlocker = s.adBlocker,
                    trackerProtection = s.trackerProtection,
                    autoUpdateInterval = s.autoUpdateInterval,
                    dohEnabled = s.dohEnabled,
                    dohUrl = s.dohUrl
                )
                onSettingsRestored(settings)
            }

            // Restore custom domains
            if (restoreDomains && backupData.customDomains.isNotEmpty()) {
                backupData.customDomains.forEach { domainBackup ->
                    val domain = CustomDomain(
                        domain = domainBackup.domain,
                        description = domainBackup.description,
                        isRegex = domainBackup.isRegex,
                        isAllowlist = domainBackup.isAllowlist,
                        isEnabled = domainBackup.isEnabled
                    )
                    customDomainRepository.insertDomain(domain)
                }
            }

            // Restore custom blocklists and allow/deny exceptions
            if (restoreBlocklists) {
                val configuration = Configuration.load()
                backupData.customBlocklists.forEach { blocklistBackup ->
                    val state = when (blocklistBackup.state) {
                        "DENY" -> HostState.DENY
                        "ALLOW" -> HostState.ALLOW
                        else -> HostState.IGNORE
                    }
                    configuration.addURL(
                        title = blocklistBackup.title,
                        location = blocklistBackup.url,
                        state = state
                    )
                }
                backupData.hostExceptions.forEach { exceptionBackup ->
                    val state = when (exceptionBackup.state) {
                        "DENY" -> HostState.DENY
                        "ALLOW" -> HostState.ALLOW
                        else -> HostState.IGNORE
                    }
                    configuration.hosts.exceptions.add(
                        HostException(
                            title = exceptionBackup.title,
                            data = exceptionBackup.hostname,
                            state = state
                        )
                    )
                }
                // A backup can contain duplicates or two variants of the same
                // list (for example TIF full and TIF mini). Keep one per group.
                val removed = BlocklistSelection.sanitize(configuration.hosts.items)
                if (removed.isNotEmpty()) {
                    Logger.info(
                        "Backup restore: removed ${removed.size} duplicate or redundant blocklist entr(y/ies)"
                    )
                }
                configuration.save()
            }

            // Restore application rules
            if (restoreApplications && backupData.applications.isNotEmpty()) {
                applicationUseCases.getApplications.execute().fold(
                    ifSuccess = { installedApps ->
                        // Only restore rules for apps that are currently installed
                        backupData.applications.forEach { appBackup ->
                            installedApps.find { it.packageID == appBackup.packageID }?.let { app ->
                                val updatedApp = app.copy(
                                    internetAccess = appBackup.internetAccess,
                                    cellularAccess = appBackup.cellularAccess,
                                    bypassVpn = appBackup.bypassVpn,
                                    isPinned = appBackup.isPinned
                                )
                                applicationUseCases.updateApplication.execute(updatedApp)
                            }
                        }
                    },
                    ifFailure = { error ->
                        Logger.error("Failed to restore application rules: ${error.message}")
                    }
                )
            }

            Logger.info("Backup imported successfully")
            Result.success(backupData)
        } catch (e: Exception) {
            Logger.error("Failed to import backup", e)
            Result.failure(e)
        }
    }
}
