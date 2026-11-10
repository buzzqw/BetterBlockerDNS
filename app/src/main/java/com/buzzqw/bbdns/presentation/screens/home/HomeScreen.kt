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

package com.buzzqw.bbdns.presentation.screens.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.snapshotFlow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.SignalCellularOff
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.buzzqw.bbdns.presentation.screens.home.components.LocalHomeSnackbar
import com.buzzqw.bbdns.service.utils.manager.ProtectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.extensions.*
import com.buzzqw.bbdns.domain.model.Application
import com.buzzqw.bbdns.domain.model.NetworkStatsState
import com.buzzqw.bbdns.presentation.components.PermissionModal
import com.buzzqw.bbdns.presentation.components.ComprehensivePermissionModal
import com.buzzqw.bbdns.presentation.components.OnboardingOverlay
import com.buzzqw.bbdns.presentation.components.material.MaterialButton
import com.buzzqw.bbdns.presentation.components.material.MaterialScaffold
import com.buzzqw.bbdns.presentation.screens.home.components.BottomMenu
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import com.buzzqw.bbdns.presentation.screens.home.components.HomeDashboard
import com.buzzqw.bbdns.presentation.screens.home.components.HomeOnboardingOverlay
import com.buzzqw.bbdns.presentation.screens.home.components.HomeTopBar
import com.buzzqw.bbdns.presentation.screens.home.components.SearchBar
import com.buzzqw.bbdns.presentation.theme.BbdnsShapes
import androidx.compose.ui.geometry.Rect
import com.buzzqw.bbdns.presentation.screens.home.viewModel.HomeViewModel
import com.buzzqw.bbdns.presentation.screens.home.viewModel.ApplicationListState
import androidx.hilt.navigation.compose.hiltViewModel
import android.graphics.drawable.Drawable
import android.widget.Toast
import com.buzzqw.bbdns.presentation.screens.settings.components.IconType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingDialog
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs.viewModel.LogsViewModel
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingType
import com.buzzqw.bbdns.presentation.screens.settings.components.SettingsBox
import com.buzzqw.bbdns.presentation.components.material.MaterialText
import com.buzzqw.bbdns.presentation.components.CircleWrapper
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.dimensionResource
import com.buzzqw.bbdns.core.utils.extensions.toBitmap
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.service.firewall.utils.FirewallStatus
import com.buzzqw.bbdns.service.firewall.utils.DnsFilterExclusions
import com.buzzqw.bbdns.service.vpn.network.util.DohProviders
import com.buzzqw.bbdns.service.vpn.network.util.NetworkConstants
import com.buzzqw.bbdns.domain.model.withDnsFilterExcluded
import com.buzzqw.bbdns.presentation.screens.home.components.AppFilterPreset
import com.buzzqw.bbdns.presentation.screens.home.components.AppFilterPresets
import com.buzzqw.bbdns.service.utils.manager.FirewallMode
import com.buzzqw.bbdns.service.utils.manager.VpnManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onLogsClicked: () -> Unit,
    homeViewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onSettingsClicked: () -> Unit,
    onStatsClicked: () -> Unit,
    onOpenLists: () -> Unit,
    onApplicationClicked: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val disableState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val isFirewallManager = homeViewModel.firewallManager.rulesLoaded.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Power button bounds (window coordinates), highlighted by the onboarding.
    var powerButtonBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(Unit) {
        homeViewModel.initialize(settingsViewModel)
    }

    // Refresh visible applications when returning to HomeScreen and close menu when navigating away
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            // When navigating away, close the menu and mark that we should refresh on return
            homeViewModel.updateMenuStatus(false)
        }
    }

    androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.let { lifecycle ->
        androidx.compose.runtime.DisposableEffect(lifecycle) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    homeViewModel.refreshVisibleApplications()
                }
            }
            lifecycle.addObserver(observer)
            onDispose {
                lifecycle.removeObserver(observer)
            }
        }
    }

    fun onMenuClosed() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                homeViewModel.updateMenuStatus(false)
            }
        }
    }

    settingsViewModel.settings.value.logs.let {
        if (homeViewModel.menuStatus.value) {
            BottomMenu(
                sheetState = sheetState,
                onSettingsClicked = onSettingsClicked,
                onLogsClicked = onLogsClicked,
                onExit = { onMenuClosed() }
            )
        }
    }

    CompositionLocalProvider(LocalHomeSnackbar provides snackbarHostState) {
    MaterialScaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HomeTopBar(
                onLogsClicked = onLogsClicked,
                onSettingsClicked = onSettingsClicked,
            )
        },
        content = {
            if (homeViewModel.firewallClicked.value) {
                fun onClick() {
                    scope.launch {
                        disableState.hide()
                    }.invokeOnCompletion {
                        homeViewModel.setFirewallClicked(false)
                    }
                }
                if (settingsViewModel.settings.value.showDialog) {
                    PermissionModal(
                        permissionName = stringResource(id = R.string.home_firewall_disable),
                        permissionDescription = stringResource(id = R.string.home_firewall_permission_desc),
                        permissionRequest = stringResource(id = R.string.common_disable),
                        onDismiss = {
                            onClick()
                        },
                        onPermissionRequest =  {
                            handleFirewallClick(homeViewModel, context, settingsViewModel, isFirewallManager.value)
                            onClick()
                        },
                        sheetState = disableState,
                        permissionAlternative = stringResource(id = R.string.common_cancel),
                        onPermissionAlternativeRequest = {
                            onClick()
                        }
                    )
                } else {
                    handleFirewallClick(homeViewModel, context, settingsViewModel, isFirewallManager.value)
                    homeViewModel.setFirewallClicked(false)
                }
            }

            HomeScreenContent(
                settingsViewModel = settingsViewModel,
                homeViewModel = homeViewModel,
                context = context,
                onApplicationClicked = onApplicationClicked,
                onStatsClicked = onStatsClicked,
                onOpenLists = onOpenLists,
                onPowerButtonPositioned = { powerButtonBounds = it },
            )
        }
    )
    }

    // Progress dialog overlay - appears on top of everything
    if (isFirewallManager.value.name() == FirewallStatus.LOADING().name()) {
        MaterialYouProgressDialog(
            progress = isFirewallManager.value.getRulesLoaded(),
            onDismiss = { /* Cannot dismiss during loading */ }
        )
    }

    // Onboarding overlay for first-time users - show regardless of firewall status
    if (settingsViewModel.settings.value.isFirstTimeRunning) {
        HomeOnboardingOverlay(
            target = powerButtonBounds,
            onDismiss = {
                settingsViewModel.update(
                    settingsViewModel.settings.value.copy(isFirstTimeRunning = false)
                )
            },
            onPowerClick = {
                settingsViewModel.update(settingsViewModel.settings.value.copy(dontShowHelp = true))
                handleFirewallClick(homeViewModel, context, settingsViewModel, isFirewallManager.value)
            }
        )
    }

    HandleComprehensivePermissions(homeViewModel, context, settingsViewModel, isFirewallManager.value)
}

@Composable
private fun HomeScreenContent(
    settingsViewModel: SettingsViewModel,
    homeViewModel: HomeViewModel,
    context: Context,
    onApplicationClicked: (String) -> Unit,
    onStatsClicked: () -> Unit,
    onOpenLists: () -> Unit,
    onPowerButtonPositioned: (Rect) -> Unit,
) {
    val applicationState = homeViewModel.applicationState.value
    val searchQuery = homeViewModel.searchQuery.value
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val logsViewModel: LogsViewModel = hiltViewModel()
    val stats by logsViewModel.networkStats.collectAsState()

    val settingsValue = settingsViewModel.settings.value
    val resolverName = NetworkConstants.DNS_SERVERS
        .firstOrNull { it.ipv4Primary == settingsValue.dnsServer1 }
        ?.name
        ?: settingsValue.dnsServer1
    val dnsLabel = if (settingsValue.dohEnabled) {
        context.getString(
            R.string.home_dns_doh,
            DohProviders.nameOrNull(settingsValue.dohUrl) ?: context.getString(R.string.doh_custom),
        )
    } else {
        resolverName
    }

    // Track animation state for search changes only
    var animationKey by remember { mutableStateOf(0) }
    var previousSearchQuery by remember { mutableStateOf(searchQuery) }
    var previousAppList by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFirstLoad by remember { mutableStateOf(true) }

    // Animate on search changes and initial load only
    LaunchedEffect(applicationState) {
        if (applicationState is ApplicationListState.Success) {
            val currentAppList = applicationState.applications.map { it.packageID }

            if (isFirstLoad) {
                isFirstLoad = false
                animationKey++
                previousAppList = currentAppList
            } else if (searchQuery != previousSearchQuery) {
                // Search query changed - animate
                if (currentAppList != previousAppList) {
                    previousSearchQuery = searchQuery
                    previousAppList = currentAppList
                    animationKey++
                }
            } else if (currentAppList != previousAppList) {
                // List structure changed but not search - don't animate to prevent scroll interference
                previousAppList = currentAppList
            }
        }
    }

    // The power button only exists on the dashboard: forget its bounds on the
    // Apps tab so the first-run hint does not point at a stale position.
    LaunchedEffect(selectedTab) {
        if (selectedTab != 0) onPowerButtonPositioned(Rect.Zero)
    }

    // The dashboard (with the power button) must be usable even while the
    // application list is still loading or failed to load.
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.home_tab_statistics)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.home_tab_apps)) }
            )
        }
        if (selectedTab == 0) {
            val firewallStatus = homeViewModel.firewallManager.rulesLoaded.collectAsState().value
            val pausedUntil by homeViewModel.firewallManager.pausedUntil.collectAsState()
            HomeDashboard(
                stats = stats,
                firewallStatus = firewallStatus,
                pausedUntil = pausedUntil,
                onPause = { minutes -> homeViewModel.firewallManager.pauseProtection(minutes) },
                onResume = { homeViewModel.firewallManager.resumeProtection() },
                onPowerButtonPositioned = onPowerButtonPositioned,
                onOpenStats = onStatsClicked,
                onOpenLists = onOpenLists,
                dnsLabel = dnsLabel,
                stabilityTipDismissed = settingsViewModel.settings.value.stabilityTipDismissed,
                onDismissStabilityTip = {
                    settingsViewModel.update(
                        settingsViewModel.settings.value.copy(stabilityTipDismissed = true)
                    )
                },
                onToggleProtection = {
                    settingsViewModel.update(settingsViewModel.settings.value.copy(dontShowHelp = true))
                    if (firewallStatus == FirewallStatus.ONLINE) {
                        // Honours "confirm before disabling".
                        homeViewModel.setFirewallClicked(true)
                    } else {
                        handleFirewallClick(
                            homeViewModel,
                            context,
                            settingsViewModel,
                            firewallStatus,
                        )
                    }
                }
            )
        } else {
            // The app search belongs to the Apps tab only.
            var presetDialog by remember { mutableStateOf<AppFilterPreset?>(null) }
            var presetTargets by remember { mutableStateOf<List<Application>>(emptyList()) }

            if (applicationState is ApplicationListState.Success) {
                AppPresetRow(
                    onPresetSelected = { preset ->
                        val targets = AppFilterPresets.select(applicationState.applications, preset)
                        if (targets.isEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.apps_preset_none),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            presetTargets = targets
                            presetDialog = preset
                        }
                    },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SearchBar(
                query = searchQuery,
                onQueryChange = { homeViewModel.updateSearchQuery(it) },
                onClearClick = { homeViewModel.clearSearchQuery() },
                button = {},
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
            when (applicationState) {
                is ApplicationListState.Success -> ProfessionalApplicationList(
                    applications = applicationState.applications,
                    viewModel = homeViewModel,
                    settingsViewModel = settingsViewModel,
                    onApplicationClicked = onApplicationClicked,
                    animationKey = animationKey,
                    modifier = Modifier.weight(1f)
                )
                is ApplicationListState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                is ApplicationListState.Error -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = applicationState.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }

            val preset = presetDialog
            if (preset != null) {
                val count = presetTargets.size
                AlertDialog(
                    onDismissRequest = { presetDialog = null },
                    title = {
                        Text(
                            context.getString(
                                R.string.apps_preset_dialog_title,
                                context.getString(preset.labelRes()),
                            )
                        )
                    },
                    text = {
                        Text(
                            context.getString(
                                if (preset.skipsDnsFilter) {
                                    R.string.apps_preset_dialog_desc_gms
                                } else {
                                    R.string.apps_preset_dialog_desc
                                },
                                count,
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (preset.skipsDnsFilter) {
                                var settingsValue = settingsViewModel.settings.value
                                presetTargets.forEach { app ->
                                    settingsValue = settingsValue.withDnsFilterExcluded(app.packageID, true)
                                }
                                settingsViewModel.update(settingsValue) {
                                    DnsFilterExclusions.refresh(
                                        context,
                                        settingsValue.dnsFilterExcludedPackages,
                                    )
                                }
                            } else {
                                homeViewModel.setBypassVpn(presetTargets, true)
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.apps_preset_done, count),
                                Toast.LENGTH_SHORT,
                            ).show()
                            presetDialog = null
                        }) {
                            Text(stringResource(R.string.apps_preset_apply))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { presetDialog = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }
        }
    }
}

/** Resource id with the chip label of a preset. */
private fun AppFilterPreset.labelRes(): Int = when (this) {
    AppFilterPreset.BANKING -> R.string.apps_presets_banking
    AppFilterPreset.SYSTEM -> R.string.apps_presets_system
    AppFilterPreset.GOOGLE_SERVICES -> R.string.apps_presets_gms
}

/** Horizontal chips with the one-tap exclusions of the Apps tab. */
@Composable
private fun AppPresetRow(onPresetSelected: (AppFilterPreset) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        AppFilterPreset.entries.forEach { preset ->
            FilterChip(
                selected = false,
                onClick = { onPresetSelected(preset) },
                label = {
                    Text(
                        text = stringResource(preset.labelRes()),
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfessionalApplicationList(
    applications: List<Application>,
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    onApplicationClicked: (String) -> Unit,
    animationKey: Int = 0,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.savedScrollIndex.value,
        initialFirstVisibleItemScrollOffset = viewModel.savedScrollOffset.value
    )

    // Save scroll position when scrolling
    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            viewModel.saveScrollPosition(index, offset)
        }
    }

    // Track visible items for controlled animations
    val visibleItems = remember(animationKey) { mutableStateMapOf<String, Boolean>() }
    val applicationIds = remember(applications) { applications.map { it.packageID } }

    // Control animations based on animation key
    LaunchedEffect(animationKey) {
        if (animationKey > 0) {
            // Clear visibility and stagger for animation (only on search/initial load)
            visibleItems.clear()
            applicationIds.forEachIndexed { index, packageId ->
                delay(25L) // Faster stagger to reduce scroll interference
                visibleItems[packageId] = true
            }
        } else {
            // No animation, make everything visible immediately
            applicationIds.forEach { packageId ->
                visibleItems[packageId] = true
            }
        }
    }

    // Ensure new items become visible immediately during normal updates
    LaunchedEffect(applicationIds) {
        applicationIds.forEach { packageId ->
            if (!visibleItems.containsKey(packageId)) {
                visibleItems[packageId] = true
            }
        }
    }

    // Load icons for visible applications - debounced to prevent excessive calls
    val primaryColor = MaterialTheme.colorScheme.primary
    LaunchedEffect(applications.size) {
        Logger.info("HomeScreen: Applications list size changed to ${applications.size}")
        if (applications.isNotEmpty()) {
            // Small delay to debounce rapid size changes during pagination
            kotlinx.coroutines.delay(50)
            viewModel.loadIcons(
                applications = applications,
                settingsViewModel = settingsViewModel,
                color = primaryColor
            )
        }
    }

    // Pagination removed - all applications are loaded at once
    
    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(18.dp, 18.dp, 0.dp, 0.dp)),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        itemsIndexed(
            items = applications,
            key = { _, application -> application.packageID },
            contentType = { _, _ -> "application_item" }
        ) { index, application ->
            val icon = viewModel.iconMap.value[application.packageID]

            val shape = when {
                applications.size == 1 -> RoundedCornerShape(32.dp)
                index == 0 -> RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                index == applications.lastIndex -> RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
                else -> RoundedCornerShape(0.dp)
            }

            if (icon != null) {
                val description = application.packageID

                val isVisible = visibleItems[application.packageID] == true

                AnimatedVisibility(
                    visible = isVisible,
                    enter = if (animationKey > 0) {
                        fadeIn(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) + slideInVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 6 }
                        )
                    } else {
                        EnterTransition.None
                    },
                    exit = ExitTransition.None,
                    modifier = Modifier.animateItemPlacement(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier.clip(shape)
                    ) {
                        ProfessionalApplicationItem(
                            application = application,
                            displayName = application.displayName.ifEmpty { application.packageID },
                            description = description,
                            icon = icon,
                            settingsViewModel = settingsViewModel,
                            viewModel = viewModel,
                            onApplicationClicked = onApplicationClicked
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun ProfessionalApplicationItem(
    application: Application,
    displayName: String,
    description: String,
    icon: Drawable,
    settingsViewModel: SettingsViewModel,
    viewModel: HomeViewModel,
    onApplicationClicked: (String) -> Unit
) {
    Box(
        modifier = Modifier.alpha(if (application.bypassVpn) 0.5f else 1f)
    ) {
        CustomSettingsBox(
            title = displayName,
            description = description,
            icon = IconType.DrawableIcon(icon),
            actionType = SettingType.CUSTOM,
            circleWrapperColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            circleWrapperSize = if (settingsViewModel.settings.value.useDynamicIcons) 6.dp else 0.dp,
            customButton = {
                AccessControlButtons(
                    packageEntity = application,
                    viewModel = viewModel,
                    settingsViewModel = settingsViewModel
                )
            },
            customAction = { onApplicationClicked(application.packageID) },
            usesGMS = application.usesGooglePlayServices,
            onLongClick = { viewModel.togglePinned(application) },
            isPinned = application.isPinned
        )
    }
}

/** The "open connections were closed" hint is shown once per app process. */
private var connectionsResetHintShown = false

@Composable
private fun AccessControlButtons(
    packageEntity: Application,
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel
) {
    var wifiAccess by remember(packageEntity.packageID, packageEntity.internetAccess) {
        mutableStateOf(packageEntity.internetAccess)
    }
    var cellularAccess by remember(packageEntity.packageID, packageEntity.cellularAccess) {
        mutableStateOf(packageEntity.cellularAccess)
    }

    // Check if app is bypassing VPN
    val isBypassed = packageEntity.bypassVpn

    if (isBypassed) {
        // Show only VPN bypass icon for bypassed apps
        Icon(
            imageVector = Icons.Rounded.VpnLock,
            contentDescription = stringResource(R.string.home_cd_vpn_bypassed),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .size(24.dp)
        )
    } else {
        // Show normal wifi/cellular controls
        val snackbar = LocalHomeSnackbar.current
        val hintScope = rememberCoroutineScope()
        val resetHint = stringResource(R.string.apps_rule_connections_reset)
        // Blocking an app ends its open connections (see SessionInvalidator):
        // explain it once, so a dropped download or call is not a surprise.
        val onBlocked = {
            if (ProtectionState.isRunning && !connectionsResetHintShown && snackbar != null) {
                connectionsResetHintShown = true
                hintScope.launch { snackbar.showSnackbar(resetHint, duration = SnackbarDuration.Long) }
            }
        }
        Row {
            IconButton(onClick = {
                wifiAccess = !wifiAccess
                viewModel.updatePackage(packageEntity.copy(internetAccess = wifiAccess), updateUI = true)
                if (!wifiAccess) onBlocked()
            }) {
                Icon(
                    imageVector = if (wifiAccess) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                    contentDescription = stringResource(
                        if (wifiAccess) R.string.apps_cd_wifi_allowed else R.string.apps_cd_wifi_blocked
                    ),
                    tint = getAccessControlTint(wifiAccess),
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            IconButton(onClick = {
                cellularAccess = !cellularAccess
                viewModel.updatePackage(packageEntity.copy(cellularAccess = cellularAccess), updateUI = true)
                if (!cellularAccess) onBlocked()
            }) {
                Icon(
                    imageVector = if (cellularAccess) Icons.Rounded.SignalCellularAlt else Icons.Rounded.SignalCellularOff,
                    contentDescription = stringResource(
                        if (cellularAccess) R.string.apps_cd_mobile_allowed else R.string.apps_cd_mobile_blocked
                    ),
                    tint = getAccessControlTint(cellularAccess)
                )
            }
        }
    }
}

private fun handleFirewallClick(homeViewModel: HomeViewModel, context: Context, settingsViewModel: SettingsViewModel, isFirewallManager: FirewallStatus) {
    val isEnabling = isFirewallManager != FirewallStatus.ONLINE
    val settings = settingsViewModel.settings.value
    
    if (isEnabling) {
        if (VpnManager.permissionChecker(context)) {
            homeViewModel.updateFirewallStatus(isFirewallManager.not(), FirewallMode.VPN)
        } else {
            homeViewModel.updateVpnPermissionStatus(true)
        }
    } else {
        homeViewModel.updateFirewallStatus(FirewallStatus.OFFLINE, FirewallMode.VPN)
    }
}

@Composable
private fun getAccessControlTint(isAccessEnabled: Boolean): Color {
    return if (isAccessEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandleComprehensivePermissions(homeViewModel: HomeViewModel, context: Context, settingsViewModel: SettingsViewModel, isFirewallManager: FirewallStatus) {
    val comprehensiveSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    if (homeViewModel.vpnPermissionRequested.value && isFirewallManager != FirewallStatus.ONLINE) {
        ComprehensivePermissionModal(
            context = context,
            sheetState = comprehensiveSheetState,
            onPermissionsComplete = { useVpn ->
                settingsViewModel.update(
                    settingsViewModel.settings.value.copy(
                        isFirstTimeRunning = false
                    )
                )
                if (useVpn) {
                    homeViewModel.updateFirewallStatus(FirewallStatus.ONLINE, FirewallMode.VPN)
                }
                
                // Reset permission state
                homeViewModel.updateVpnPermissionStatus(false)
                
                // Hide the modal
                scope.launch { comprehensiveSheetState.hide() }
            },
            onDismiss = {
                homeViewModel.updateVpnPermissionStatus(false)
                scope.launch { comprehensiveSheetState.hide() }
            }
        )
        
        // Auto-show the modal when permission is requested
        LaunchedEffect(homeViewModel.vpnPermissionRequested.value) {
            if (homeViewModel.vpnPermissionRequested.value) {
                comprehensiveSheetState.show()
            }
        }
    }
}

@Composable
private fun MaterialYouProgressDialog(
    progress: Float,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(24.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Header with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_applying_rules),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Progress information
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Circular progress with percentage
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(80.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 6.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Progress stages text
                        Text(
                            text = getProgressStageText(progress),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Warning section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = BbdnsShapes.card,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.home_warning_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.home_setup_warning),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun getProgressStageText(progress: Float): String {
    val currentCommand = (progress * 685).toInt() // Approximate total commands based on your logs
    val totalCommands = 685
    
    return when {
        progress < 0.01f -> stringResource(R.string.home_preparing_commands)
        progress < 1.0f -> stringResource(R.string.home_executing_commands, currentCommand, totalCommands)
        else -> stringResource(R.string.home_rules_applied)
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomSettingsBox(
    title: String,
    description: String,
    icon: IconType,
    actionType: SettingType,
    circleWrapperColor: Color,
    circleWrapperSize: androidx.compose.ui.unit.Dp,
    customButton: @Composable () -> Unit,
    customAction: () -> Unit,
    usesGMS: Boolean,
    onLongClick: (() -> Unit)? = null,
    isPinned: Boolean = false
) {
    val context = LocalContext.current
    var showCustomAction by remember { mutableStateOf(false) }

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .padding(bottom = dimensionResource(id = R.dimen.card_padding_bottom))
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { customAction() },
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable { customAction() }
                }
            )
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(
                    horizontal = dimensionResource(id = R.dimen.card_padding_horizontal),
                    vertical = 12.dp
                )
                .fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Row(
                Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Box {
                    when (icon) {
                        is IconType.DrawableIcon -> {
                            CircleWrapper(
                                size = circleWrapperSize,
                                color = circleWrapperColor
                            ) {
                                Image(
                                    bitmap = icon.drawable.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .height(38.dp - circleWrapperSize)
                                        .width(38.dp - circleWrapperSize)
                                )
                            }
                        }
                        is IconType.VectorIcon -> {
                            CircleWrapper(
                                size = 12.dp,
                                color = circleWrapperColor
                            ) {
                                Icon(
                                    imageVector = icon.imageVector,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Pinned indicator - Material 3 filled pin icon at bottom right
                    if (isPinned) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(y = 4.dp) // Move down for better positioning
                                .size(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = stringResource(R.string.home_cd_pinned),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                
                if (usesGMS) {
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                        Text(
                            text = description.substringBefore('\n'),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_gms_warning),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                } else {
                    MaterialText(
                        title = title,
                        description = description
                    )
                }
            }
            customButton()
        }
    }
}

