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

package com.buzzqw.bbdns.presentation.screens.home.components

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.components.DownloadDialog
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.components.DownloadState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.HageziCatalog
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.HageziViewModel
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.NumberFormatter
import com.buzzqw.bbdns.domain.model.NetworkStatsState
import com.buzzqw.bbdns.presentation.screens.home.viewModel.HomeInsightsViewModel
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.RuleDatabase.Companion.ListStat
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.lists.BlocklistNames
import com.buzzqw.bbdns.presentation.screens.stats.LiveTrafficCard
import com.buzzqw.bbdns.presentation.screens.stats.TrafficSampler
import com.buzzqw.bbdns.presentation.screens.stats.rememberLiveTick
import com.buzzqw.bbdns.presentation.theme.BbdnsShapes
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters
import com.buzzqw.bbdns.service.firewall.utils.FirewallStatus
import com.buzzqw.bbdns.service.utils.manager.ProtectionPause
import com.buzzqw.bbdns.service.utils.manager.ProtectionState
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.roundToInt

/** Space between the dashboard sections and cards. */
private val SectionSpacing = 16.dp

/**
 * Opening dashboard, top to bottom: protection status with the single power
 * control and the pause control, warnings, key numbers, activity and
 * history charts, then the most and recently blocked domains.
 */
@Composable
fun HomeDashboard(
    stats: NetworkStatsState,
    firewallStatus: FirewallStatus?,
    pausedUntil: Long,
    onToggleProtection: () -> Unit,
    onPause: (minutes: Int) -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
    dnsLabel: String = "",
    stabilityTipDismissed: Boolean = true,
    onDismissStabilityTip: () -> Unit = {},
    onPowerButtonPositioned: (Rect) -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenLists: () -> Unit = {},
) {
    val insightsViewModel: HomeInsightsViewModel = hiltViewModel()
    val allowedFromHome by insightsViewModel.allowedDomains.collectAsState()
    val history by insightsViewModel.history.collectAsState()
    val listStats by insightsViewModel.listStats.collectAsState()

    val profileViewModel: HageziViewModel = hiltViewModel()
    val catalogueUrls by profileViewModel.enabledUrls.collectAsState()
    val profileBusy by profileViewModel.busy.collectAsState()
    val profileDialogVisible by profileViewModel.showDownloadDialog.collectAsState()
    val profileDownloadState by profileViewModel.downloadState.collectAsState()
    val profileProgress by profileViewModel.progress.collectAsState()
    val profileStage by profileViewModel.stage.collectAsState()
    val profileName by profileViewModel.currentListName.collectAsState()
    val activeProfile = HageziCatalog.matchingProfile(catalogueUrls)
        ?: if (catalogueUrls.any { url ->
                HageziCatalog.all.any { (it.coreTier || it.threatFeed) && it.url == url }
            }
        ) {
            null
        } else {
            // No profile-managed list yet: advertise Balanced as the default.
            HageziCatalog.profiles.firstOrNull { it.id == "balanced" }
        }
    var pickProfile by remember { mutableStateOf(false) }
    var domainToAllow by remember { mutableStateOf<String?>(null) }

    val isActive = firewallStatus == FirewallStatus.ONLINE
    val isBusy = firewallStatus is FirewallStatus.LOADING
    val isPaused = isActive && pausedUntil > 0L

    // While protection runs, show the live session counters kept by the VPN
    // service (they work even with log persistence disabled) and refresh the
    // session duration on its own; otherwise fall back to the log statistics.
    // The tick is lifecycle-aware: it stops while the screen is in background,
    // which keeps the dashboard cheap on battery.
    val now = rememberLiveTick(intervalMs = if (isActive) 2_000L else 15_000L)
    val sessionStart = ProtectionState.sessionStartedAt.takeIf { it > 0 } ?: stats.sessionStartTime
    // `now` is a key so the live counters are re-read on every tick.
    val shownStats = remember(now, isActive, stats) {
        if (isActive && ProtectionState.isRunning) {
            val (allowed, blocked) = FirewallCounters.snapshot()
            NetworkStatsState(
                allowedCount = allowed,
                blockedCount = blocked,
                totalCount = allowed + blocked,
                isFirewallActive = true,
                sessionStartTime = sessionStart,
            )
        } else {
            stats.copy(sessionStartTime = sessionStart)
        }
    }

    val context = LocalContext.current
    // Re-read on every tick: the user may change Private DNS and come back.
    val privateDnsHost = remember(now) { strictPrivateDnsHost(context) }
    val details = remember(now, isActive) {
        if (isActive && ProtectionState.isRunning) FirewallCounters.details() else null
    }
    val traffic = remember(now) { TrafficSampler.snapshot() }
    val stability = remember(now, isActive) { stabilityStatus(context, ProtectionState.alwaysOn) }
    val resumeTime = remember(pausedUntil) {
        if (pausedUntil > 0L) DateFormat.getTimeFormat(context).format(Date(pausedUntil)) else ""
    }

    val statusColor = when {
        isBusy || isPaused -> MaterialTheme.colorScheme.tertiary
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        // 1. Status, power and pause.
        StatusSection(
            isActive = isActive,
            isBusy = isBusy,
            isPaused = isPaused,
            resumeTime = resumeTime,
            statusColor = statusColor,
            savedBytes = FirewallCounters.estimatedSavedBytes(shownStats.blockedCount),
            onToggleProtection = onToggleProtection,
            onPause = onPause,
            onResume = onResume,
            onPowerButtonPositioned = onPowerButtonPositioned,
        )

        if (dnsLabel.isNotBlank()) {
            DnsInfoRow(dnsLabel)
        }

        // Protection profile: visible and selectable straight from the home.
        ProfileCard(
            profileName = activeProfile?.let { stringResource(it.nameRes) }
                ?: stringResource(R.string.blocklists_profile_custom),
            profileDescription = activeProfile?.let { stringResource(it.descriptionRes) }
                ?: stringResource(R.string.home_profile_hint),
            busy = profileBusy,
            onClick = { pickProfile = true },
        )

        // 2. Anything that needs the user's attention.
        if (privateDnsHost != null) {
            PrivateDnsWarningCard(host = privateDnsHost)
        }
        if (!isActive && ProtectionState.revokedByOtherVpn) {
            VpnRevokedCard()
        }
        if (isActive && !stabilityTipDismissed && stability.needsAttention) {
            StabilityTipCard(status = stability, onDismiss = onDismissStabilityTip)
        }

        // 3. Key numbers.
        HeroStatsCard(
            stats = shownStats,
            isActive = isActive,
            now = now,
            onClick = onOpenStats,
            onBlockedClick = onOpenLists,
        )

        // 4. Live speed and blocks.
        LiveTrafficCard(
            traffic = traffic,
            blockedPerMinute = details?.blockedPerMinute ?: emptyList(),
            onClick = onOpenStats,
        )

        // 5. Active blocklists at a glance (tap to manage them).
        ActiveListsCard(listStats = listStats, onClick = onOpenLists)

        // 6. Activity of the last 30 minutes and daily history.
        details?.let { ActivityChartCard(details = it, onClick = onOpenStats) }
        HistoryCard(history = history)

        // 7. Blocked domains (tap one to allow it).
        details?.let {
            TopBlockedCard(
                top = it.topBlocked.filter { (domain, _) -> domain !in allowedFromHome },
                onDomainClick = { domain -> domainToAllow = domain },
            )
            RecentBlockedCard(
                recent = it.recentBlocked.filter { event -> event.domain !in allowedFromHome },
                now = now,
                onDomainClick = { domain -> domainToAllow = domain },
            )
        }
    }

    if (pickProfile) {
        AlertDialog(
            onDismissRequest = { pickProfile = false },
            title = { Text(stringResource(R.string.blocklists_profile_dialog_title)) },
            text = {
                Column {
                    HageziCatalog.profiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !profileBusy) {
                                    profileViewModel.applyProfile(profile)
                                    pickProfile = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = activeProfile?.id == profile.id,
                                onClick = {
                                    if (!profileBusy) {
                                        profileViewModel.applyProfile(profile)
                                        pickProfile = false
                                    }
                                },
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = stringResource(profile.nameRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = stringResource(profile.descriptionRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickProfile = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (profileDialogVisible) {
        DownloadDialog(
            title = profileName ?: stringResource(R.string.home_profile_busy),
            isVisible = true,
            downloadState = profileDownloadState ?: DownloadState.Downloading,
            progress = profileProgress,
            stage = profileStage,
            onDismiss = { profileViewModel.dismissDownloadDialog() },
        )
    }

    domainToAllow?.let { domain ->
        AllowDomainDialog(
            domain = domain,
            onConfirm = {
                insightsViewModel.allowDomain(domain)
                domainToAllow = null
            },
            onDismiss = { domainToAllow = null },
        )
    }
}

@Composable
private fun StatusSection(
    isActive: Boolean,
    isBusy: Boolean,
    isPaused: Boolean,
    resumeTime: String,
    statusColor: Color,
    savedBytes: Long,
    onToggleProtection: () -> Unit,
    onPause: (Int) -> Unit,
    onResume: () -> Unit,
    onPowerButtonPositioned: (Rect) -> Unit,
) {
    val title = when {
        isBusy -> stringResource(R.string.home_status_loading)
        isPaused -> stringResource(R.string.home_status_paused)
        isActive -> stringResource(R.string.home_status_protected)
        else -> stringResource(R.string.home_status_unprotected)
    }
    val description = when {
        isBusy -> stringResource(R.string.home_status_loading_desc)
        isPaused -> stringResource(R.string.home_status_paused_desc, resumeTime)
        isActive -> stringResource(R.string.home_status_protected_desc)
        else -> stringResource(R.string.home_status_unprotected_desc)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HomePowerButton(
            isActive = isActive,
            isBusy = isBusy,
            color = statusColor,
            stateText = title,
            onClick = onToggleProtection,
            size = 128.dp,
            modifier = Modifier.onBoundsInWindow(onPowerButtonPositioned),
        )

        Spacer(modifier = Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.semantics {
                    heading()
                    liveRegion = LiveRegionMode.Polite
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            if (isActive) {
                Spacer(modifier = Modifier.height(10.dp))
                // Traffic estimate: how much was not downloaded thanks to blocking.
                Row(
                    modifier = Modifier
                        .clip(BbdnsShapes.chip)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDownload,
                        contentDescription = stringResource(R.string.stats_data_saved),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.home_saved_data,
                            NumberFormatter.formatBytes(savedBytes),
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (isActive) {
                Spacer(modifier = Modifier.height(10.dp))
                if (isPaused) {
                    FilledTonalButton(onClick = onResume) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.home_resume_now))
                    }
                } else {
                    PauseButton(onPause = onPause)
                }
            }
        }
    }
}

/** Outlined "Pause" button opening a menu with the pause durations. */
@Composable
private fun PauseButton(onPause: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val resources = LocalContext.current.resources
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.Pause,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.home_pause))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ProtectionPause.PRESETS.forEach { minutes ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (minutes % 60 == 0) {
                                resources.getQuantityString(R.plurals.home_pause_hours, minutes / 60, minutes / 60)
                            } else {
                                resources.getQuantityString(R.plurals.home_pause_minutes, minutes, minutes)
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onPause(minutes)
                    },
                )
            }
        }
    }
}

/**
 * Key numbers of the session in one card: block rate with a proportion bar,
 * then total / blocked / allowed and the session length.
 */
@Composable
private fun DnsInfoRow(dnsLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BbdnsShapes.chip)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Dns,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.home_dns_using, dnsLabel),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileCard(
    profileName: String,
    profileDescription: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    InsightCard(
        icon = Icons.Rounded.Shield,
        title = stringResource(R.string.home_profile_title),
        trailing = {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.home_profile_hint),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
    ) {
        Text(
            text = profileName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = profileDescription,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeroStatsCard(
    stats: NetworkStatsState,
    isActive: Boolean,
    now: Long,
    onClick: () -> Unit,
    onBlockedClick: () -> Unit,
) {
    val blockRate = if (stats.totalCount > 0) {
        (stats.blockedCount * 100.0 / stats.totalCount).roundToInt()
    } else {
        0
    }
    val fraction = if (stats.totalCount > 0) {
        (stats.blockedCount.toFloat() / stats.totalCount).coerceIn(0f, 1f)
    } else {
        0f
    }
    val blockRateText = stringResource(R.string.home_block_rate_value, blockRate)
    val blockRateLabel = stringResource(R.string.home_block_rate)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.stats_title), onClick = onClick),
        shape = BbdnsShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text(
                        text = blockRateLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = blockRateText,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = stringResource(R.string.home_session),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sessionLabel(stats, isActive, now),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            val barColor = MaterialTheme.colorScheme.error
            val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(BbdnsShapes.chip)
                    .semantics { contentDescription = "$blockRateLabel $blockRateText" }
            ) {
                val radius = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(trackColor, cornerRadius = radius)
                if (fraction > 0f) {
                    drawRoundRect(
                        barColor,
                        size = Size((size.width * fraction).coerceAtLeast(size.height), size.height),
                        cornerRadius = radius,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.QueryStats,
                    label = stringResource(R.string.home_total_requests),
                    value = NumberFormatter.formatCount(stats.totalCount),
                    color = MaterialTheme.colorScheme.primary,
                )
                StatCell(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClickLabel = stringResource(R.string.home_view_lists), onClick = onBlockedClick),
                    icon = Icons.Rounded.Block,
                    label = stringResource(R.string.home_blocked_requests),
                    value = NumberFormatter.formatCount(stats.blockedCount),
                    color = MaterialTheme.colorScheme.error,
                )
                StatCell(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.CheckCircle,
                    label = stringResource(R.string.home_allowed_requests),
                    value = NumberFormatter.formatCount(stats.allowedCount),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun ActiveListsCard(
    listStats: List<ListStat>,
    onClick: () -> Unit,
) {
    val total = listStats.sumOf { it.entries.toLong() }
    InsightCard(
        icon = Icons.Rounded.List,
        title = stringResource(R.string.home_active_lists_title),
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = stringResource(R.string.home_view_lists),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        onClick = onClick,
    ) {
        if (listStats.isEmpty()) {
            Text(
                text = stringResource(R.string.home_active_lists_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = stringResource(
                    R.string.home_active_lists_summary,
                    listStats.size,
                    NumberFormatter.formatCount(total),
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            listStats.sortedByDescending { it.entries }.take(3).forEach { stat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = BlocklistNames.displayName(stat.title, stat.data),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = NumberFormatter.formatCount(stat.entries.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCell(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun sessionLabel(stats: NetworkStatsState, isActive: Boolean, now: Long): String {
    val start = stats.sessionStartTime
    if (!isActive || start == null) return stringResource(R.string.home_session_none)
    val minutes = ((now - start) / 60_000).coerceAtLeast(0)
    return when {
        minutes >= 60 -> stringResource(R.string.home_session_hours_minutes, minutes / 60, minutes % 60)
        minutes > 0 -> stringResource(R.string.home_session_minutes, minutes)
        else -> stringResource(R.string.home_session_less_minute)
    }
}

@Composable
private fun HomePowerButton(
    isActive: Boolean,
    isBusy: Boolean,
    color: Color,
    stateText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 172.dp,
) {
    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(500),
        label = "powerColor",
    )
    val scale by animateFloatAsState(
        targetValue = if (isActive || isBusy) 1f else 0.95f,
        animationSpec = tween(300),
        label = "powerScale",
    )
    val toggleDescription = stringResource(R.string.home_toggle_protection)
    val pulse = rememberInfiniteTransition(label = "powerPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive && !isBusy) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.30f),
                            Color.Transparent,
                        )
                    )
                )
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size * 0.77f)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(
                    elevation = if (isActive || isBusy) 16.dp else 6.dp,
                    shape = CircleShape,
                    ambientColor = animatedColor.copy(alpha = 0.3f),
                    spotColor = animatedColor.copy(alpha = 0.3f),
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedColor.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surface,
                        )
                    )
                )
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(animatedColor, animatedColor.copy(alpha = 0.4f))
                    ),
                    shape = CircleShape,
                )
                .clickable(enabled = !isBusy, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = toggleDescription
                    stateDescription = stateText
                }
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    color = animatedColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(size * 0.3f),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.PowerSettingsNew,
                    contentDescription = null,
                    tint = animatedColor,
                    modifier = Modifier.size(size * 0.34f),
                )
            }
        }
    }
}
