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

package com.buzzqw.bbdns.presentation.screens.stats

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.NumberFormatter
import com.buzzqw.bbdns.domain.model.NetworkStatsState
import com.buzzqw.bbdns.presentation.components.material.MaterialBar
import com.buzzqw.bbdns.presentation.components.material.MaterialScaffold
import com.buzzqw.bbdns.presentation.screens.home.components.ActivityChartCard
import com.buzzqw.bbdns.presentation.screens.home.components.AllowDomainDialog
import com.buzzqw.bbdns.presentation.screens.home.components.HistoryCard
import com.buzzqw.bbdns.presentation.screens.home.components.RecentBlockedCard
import com.buzzqw.bbdns.presentation.screens.home.components.TopBlockedCard
import com.buzzqw.bbdns.presentation.screens.home.viewModel.HomeInsightsViewModel
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs.viewModel.LogsViewModel
import com.buzzqw.bbdns.presentation.theme.BbdnsShapes
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters
import com.buzzqw.bbdns.service.utils.manager.ProtectionState
import java.util.Date
import kotlin.math.roundToInt

/**
 * Full statistics page reached from the "Block rate" and "Activity" cards of
 * the home dashboard: live traffic, session totals, saved data, per-minute
 * activity, the last 30 days and the apps that generated the most blocks.
 */
@Composable
fun StatsScreen(
    navController: NavController,
    onBack: () -> Unit,
    onOpenLists: () -> Unit,
) {
    val logsViewModel: LogsViewModel = hiltViewModel()
    val stats by logsViewModel.networkStats.collectAsState()

    MaterialScaffold(
        topBar = {
            MaterialBar(
                title = stringResource(R.string.stats_title),
                onBackNavClicked = onBack,
            )
        },
        content = {
            StatsContent(
                stats = stats,
                onOpenLogs = {
                    navController.navigate(com.buzzqw.bbdns.presentation.navigation.routes.LogRoutes.Logs.route)
                },
                onOpenLists = onOpenLists,
            )
        },
    )
}

/**
 * Content shared by the dedicated statistics page and the Statistics tab of
 * the request log.
 */
@Composable
fun StatsContent(
    stats: NetworkStatsState,
    modifier: Modifier = Modifier,
    onOpenLogs: (() -> Unit)? = null,
    onOpenLists: (() -> Unit)? = null,
) {
    val insightsViewModel: HomeInsightsViewModel = hiltViewModel()
    val history by insightsViewModel.history.collectAsState()
    var domainToAllow by remember { mutableStateOf<String?>(null) }
    val tick = rememberLiveTick(intervalMs = if (ProtectionState.isRunning) 2_000L else 15_000L)
    val running = ProtectionState.isRunning

    val shownStats = remember(tick, stats, running) {
        if (running) {
            val (allowed, blocked) = FirewallCounters.snapshot()
            NetworkStatsState(
                allowedCount = allowed,
                blockedCount = blocked,
                totalCount = allowed + blocked,
                isFirewallActive = true,
                sessionStartTime = ProtectionState.sessionStartedAt.takeIf { it > 0 }
                    ?: stats.sessionStartTime,
            )
        } else {
            stats
        }
    }
    val details = remember(tick, running) {
        if (running) FirewallCounters.details() else null
    }
    val traffic = remember(tick) { TrafficSampler.snapshot() }
    val apps = remember(tick, running) {
        if (running) {
            FirewallCounters.topApps(8).map { AppUsage(it.first, it.second, it.third) }
        } else {
            emptyList()
        }
    }

    val blockRate = if (shownStats.totalCount > 0) {
        (shownStats.blockedCount * 100.0 / shownStats.totalCount).roundToInt()
    } else {
        0
    }
    val saved = FirewallCounters.estimatedSavedBytes(shownStats.blockedCount)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LiveTrafficCard(
            traffic = traffic,
            blockedPerMinute = details?.blockedPerMinute ?: emptyList(),
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = BbdnsShapes.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.stats_overview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.QueryStats,
                        label = stringResource(R.string.stats_total),
                        value = NumberFormatter.formatCount(shownStats.totalCount),
                    )
                    StatTile(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (onOpenLists != null) {
                                    Modifier.clickable(onClick = onOpenLists)
                                } else {
                                    Modifier
                                }
                            ),
                        icon = Icons.Rounded.Block,
                        label = stringResource(R.string.stats_blocked),
                        value = NumberFormatter.formatCount(shownStats.blockedCount),
                        color = MaterialTheme.colorScheme.error,
                    )
                    StatTile(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Rounded.CheckCircle,
                        label = stringResource(R.string.stats_allowed),
                        value = NumberFormatter.formatCount(shownStats.allowedCount),
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.stats_block_rate),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.home_block_rate_value, blockRate),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = NumberFormatter.formatBytes(saved),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Text(
                            text = stringResource(R.string.stats_data_saved),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val fraction = if (shownStats.totalCount > 0) {
                    (shownStats.blockedCount.toFloat() / shownStats.totalCount).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val barColor = MaterialTheme.colorScheme.error
                val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
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
            }
        }

        details?.let { ActivityChartCard(details = it) }
        HistoryCard(history = history)

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = BbdnsShapes.card,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.stats_top_apps),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.stats_top_apps_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (apps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.stats_no_activity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TopAppsList(apps = apps)
                }
            }
        }

        if (onOpenLogs != null || onOpenLists != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = BbdnsShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    onOpenLogs?.let {
                        LinkRow(
                            label = stringResource(R.string.stats_open_logs),
                            value = NumberFormatter.formatCount(shownStats.totalCount),
                            onClick = it,
                        )
                    }
                    onOpenLists?.let {
                        LinkRow(
                            label = stringResource(R.string.stats_open_lists),
                            value = stringResource(R.string.home_view_lists),
                            onClick = it,
                        )
                    }
                }
            }
        }

        details?.let { session ->
            TopBlockedCard(
                top = session.topBlocked,
                onDomainClick = { domain -> domainToAllow = domain },
            )
            RecentBlockedCard(
                recent = session.recentBlocked,
                now = tick,
                onDomainClick = { domain -> domainToAllow = domain },
            )
        }
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
