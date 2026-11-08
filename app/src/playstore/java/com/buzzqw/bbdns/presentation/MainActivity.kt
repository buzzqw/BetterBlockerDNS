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

package com.buzzqw.bbdns.presentation

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.core.utils.extensions.popUpToTop
import com.buzzqw.bbdns.presentation.theme.EasyWallTheme
import com.buzzqw.bbdns.presentation.navigation.AppNavHost
import com.buzzqw.bbdns.presentation.navigation.routes.HomeRoutes
import com.buzzqw.bbdns.presentation.screens.home.viewModel.HomeViewModel
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.FileHelper
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.HostState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabaseUpdateWorker
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.Configuration
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.CompatibilityAllowlist
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.DefaultProtection
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import com.buzzqw.bbdns.service.utils.manager.NetworkSpeedManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import java.io.IOException
import javax.inject.Inject


var config = Configuration.load()

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel by viewModels<MainViewModel>()
    private var settings: SettingsViewModel? = null
    private lateinit var navController: NavHostController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen().apply {
            setKeepOnScreenCondition { !viewModel.isReady.value }
        }
        enableEdgeToEdge()
        // Compatibility allowlist is on by default (once).
        CompatibilityAllowlist.ensureEnabledByDefault(this, config)
        DefaultProtection.ensureEnabledByDefault(this, config)
        setupUI()
    }

    override fun onResume() {
        super.onResume()
        settings?.let {
            it.loadDefaultRoute()
            if (it.defaultRoute != HomeRoutes.Home.route) {
                if (it.settings.value.lockImmediately) {
                    navController.navigate(it.defaultRoute!!) { popUpToTop(navController) }
                }
            }
        }
    }

    private fun refresh() {
        val workRequest = OneTimeWorkRequestBuilder<RuleDatabaseUpdateWorker>()
            .build()
        WorkManager.getInstance(this).enqueue(workRequest)
    }

    private fun setupUI() {
        setContent {
            // Download the lists only when they are missing: re-downloading on
            // every launch wasted data and battery. AutoUpdateManager handles
            // the periodic refresh.
            if (!areHostsFilesExistent()) {
                refresh()
            }
            navController = rememberNavController()

            settings = hiltViewModel<SettingsViewModel>().apply {
                val homeViewModel = hiltViewModel<HomeViewModel>()

                EasyWallTheme(this) {
                    val iconsColor = MaterialTheme.colorScheme.background

                    LaunchedEffect(homeViewModel) {
                        homeViewModel.initialize(this@apply)
                        
                        // Start network speed monitor if enabled
                        if (settings.value.networkSpeedMonitor) {
                            NetworkSpeedManager.start(this@MainActivity)
                            Logger.info("Network speed monitor started on app startup")
                        }
                    }
                    
                    // Watch for when apps are loaded and hide splash screen
                    LaunchedEffect(homeViewModel.applicationState.value) {
                        if (homeViewModel.applicationState.value is com.buzzqw.bbdns.presentation.screens.home.viewModel.ApplicationListState.Success) {
                            viewModel.showSlashScreen(true)
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AppNavHost(
                            settings = this,
                            startDestination = this@apply.defaultRoute!!,
                            homeViewModel = homeViewModel,
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    private fun areHostsFilesExistent(): Boolean {
        if (!config.hosts.enabled) {
            return true
        }

        for (item in config.hosts.items) {
            if (item.state != HostState.IGNORE) {
                try {
                    val reader = FileHelper.openItemFile(item) ?: return false
                    reader.close()
                } catch (e: IOException) {
                    Logger.error("areHostFilesExistent: Failed to open file {$item}", e)
                    return false
                }
            }
        }
        return true
    }
}