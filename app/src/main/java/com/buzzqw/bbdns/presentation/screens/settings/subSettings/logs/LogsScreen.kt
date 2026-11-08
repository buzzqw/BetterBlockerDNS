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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.extensions.*
import com.buzzqw.bbdns.domain.model.Log
import com.buzzqw.bbdns.presentation.components.material.MaterialPlaceholder
import com.buzzqw.bbdns.presentation.components.material.MaterialScaffold
import com.buzzqw.bbdns.presentation.screens.home.components.SearchBar
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs.viewModel.LogsViewModel
import com.buzzqw.bbdns.presentation.screens.settings.viewModel.SettingsViewModel
import com.buzzqw.bbdns.service.firewall.utils.Port
import java.util.Locale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import com.buzzqw.bbdns.presentation.navigation.routes.LogRoutes
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs.components.BlockReasonSheet
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs.components.NetworkStatsSection
import com.buzzqw.bbdns.service.firewall.model.FirewallResult
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogsScreen(
    onBackNavClicked: () -> Unit,
    navController: NavController,
    logsViewModel: LogsViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val networkStats = logsViewModel.networkStats.collectAsState()
    val detailedLogs = settingsViewModel.settings.value.detailedLogs
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val coroutineScope = rememberCoroutineScope()

    val filteredLogs = logsViewModel.filteredLogs.collectAsState()

    // DNS-blocked entry whose "Why is this blocked?" sheet is open.
    var reasonDomain by rememberSaveable { mutableStateOf<String?>(null) }
    var reasonLogId by rememberSaveable { mutableStateOf<Int?>(null) }
    val onBlockedClick: (Log) -> Unit = { log ->
        reasonDomain = log.destinationAddress
        reasonLogId = log.id
    }

    MaterialScaffold(
        topBar = {
            LogsTopBar(
                pagerState = pagerState,
                coroutineScope = coroutineScope,
                query = logsViewModel.query.value,
                onQueryChange = logsViewModel::onQueryChanged,
                onClearClick = logsViewModel::clearQuery,
                onDeleteClick = logsViewModel::deleteLogs,
                onBackClicked = onBackNavClicked
            )
        }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> TrafficSection(
                    logs = filteredLogs.value,
                    context = context,
                    navController = navController,
                    detailed = detailedLogs,
                    onBlockedClick = onBlockedClick
                )
                1 -> StatsSection(networkStats = networkStats.value)
            }
        }
    }

    reasonDomain?.let { domain ->
        val logId = reasonLogId
        BlockReasonSheet(
            domain = domain,
            onDismiss = {
                reasonDomain = null
                reasonLogId = null
            },
            onOpenDetails = logId?.let { id ->
                { navController.safeNavigate(LogRoutes.Packet.createRoute(id)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LogsTopBar(
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onBackClicked: () -> Unit
) {
    Column {
        SearchBar(
            text = stringResource(R.string.logs_search_packets),
            query = query,
            onQueryChange = onQueryChange,
            onClearClick = onClearClick,
            button = {
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.logs_delete_all))
                }
            },
            onBackClicked = onBackClicked
        )

        // Mode selection buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(35.dp, 0.dp, 35.dp, 30.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            SectionButton(
                pagerState = pagerState,
                coroutineScope = coroutineScope,
                pageIndex = 0,
                icon = Icons.Rounded.Description,
                label = stringResource(R.string.logs_traffic),
                shape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
            )
            SectionButton(
                pagerState = pagerState,
                coroutineScope = coroutineScope,
                pageIndex = 1,
                icon = Icons.Rounded.Analytics,
                label = stringResource(R.string.logs_stats),
                shape = RoundedCornerShape(topEnd = 32.dp, bottomEnd = 32.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowScope.SectionButton(
    pagerState: PagerState,
    coroutineScope: CoroutineScope,
    pageIndex: Int,
    icon: ImageVector,
    label: String,
    shape: RoundedCornerShape
) {
    val isSelected = pagerState.currentPage == pageIndex

    Box(
        modifier = Modifier
            .height(48.dp)
            .weight(1f)
            .clip(shape)
            .background(
                color = if (isSelected)
                    MaterialTheme.colorScheme.surfaceContainer
                else
                    MaterialTheme.colorScheme.surfaceContainerLow
            )
            .clickable {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(pageIndex)
                }
            }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                maxLines = 1,
                color = if (isSelected)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun TrafficSection(
    logs: List<List<Log>>,
    context: Context,
    navController: NavController,
    detailed: Boolean = false,
    onBlockedClick: ((Log) -> Unit)? = null
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val shown = remember(logs, filter) {
        when (filter) {
            1 -> logs.map { group -> group.filter { it.isDnsLog() } }.filter { it.isNotEmpty() }
            2 -> logs.map { group -> group.filterNot { it.isDnsLog() } }.filter { it.isNotEmpty() }
            3 -> logs.map { group -> group.filter { it.packetStatus != FirewallResult.ACCEPT } }
                .filter { it.isNotEmpty() }
            else -> logs
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                R.string.logs_filter_all,
                R.string.logs_filter_dns,
                R.string.logs_filter_packets,
                R.string.logs_filter_blocked,
            ).forEachIndexed { index, label ->
                FilterChip(
                    selected = filter == index,
                    onClick = { filter = index },
                    label = { Text(stringResource(label)) },
                )
            }
        }

        if (shown.isEmpty()) {
            MaterialPlaceholder(
                placeholderIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.scale(2f),
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                placeholderText = stringResource(R.string.logs_no_logs)
            )
        } else {
            LazyColumn(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
            ) {
                items(
                    items = shown,
                    key = { logGroup -> logGroup.firstOrNull()?.id ?: 0 }
                ) { logGroup ->
                    LogGroupCard(
                        logGroup = logGroup,
                        context = context,
                        navController = navController,
                        detailed = detailed,
                        onBlockedClick = onBlockedClick
                    )
                }
            }
        }
    }
}

@Composable
fun PacketsSection(
    logs: List<List<Log>>,
    context: Context,
    navController: NavController,
    detailed: Boolean = false,
    onBlockedClick: ((Log) -> Unit)? = null
) {
    // Packets excludes DNS entries so the two tabs never show the same rows.
    val packetsWithDomains = remember(logs) {
        logs.map { group -> group.filterNot { it.isDnsLog() } }
            .filter { it.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (packetsWithDomains.isEmpty()) {
            MaterialPlaceholder(
                placeholderIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.scale(2f),
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                placeholderText = stringResource(R.string.logs_no_logs)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
            ) {
                items(
                    items = packetsWithDomains,
                    key = { logGroup -> logGroup.firstOrNull()?.id ?: 0 }
                ) { logGroup ->
                    LogGroupCard(
                        logGroup = logGroup,
                        context = context,
                        navController = navController,
                        detailed = detailed,
                        onBlockedClick = onBlockedClick
                    )
                }
            }
        }
    }
}

/** A DNS-blocked entry with a domain can open the "Why is this blocked?" sheet. */
private fun Log.canExplainBlock(): Boolean =
    packetStatus == FirewallResult.DNS_BLOCKED && !destinationAddress.isNullOrBlank()

/** A log entry belongs to the DNS tab when it is a DNS query/answer or a DNS block. */
private fun Log.isDnsLog(): Boolean =
    destinationPort == "53" ||
        sourcePort == "53" ||
        packetStatus == FirewallResult.DNS_BLOCKED

@Composable
fun DnsRequestsSection(
    logs: List<List<Log>>,
    context: Context,
    navController: NavController,
    detailed: Boolean = false,
    onBlockedClick: ((Log) -> Unit)? = null
) {
    val dnsLogs = remember(logs) {
        logs.map { logGroup ->
            logGroup.filter { it.isDnsLog() }
        }.filter { it.isNotEmpty() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (dnsLogs.isEmpty()) {
            MaterialPlaceholder(
                placeholderIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Dns,
                        contentDescription = null,
                        modifier = Modifier.scale(2f),
                        tint = MaterialTheme.colorScheme.outline
                    )
                },
                placeholderText = stringResource(R.string.logs_no_dns_requests)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
            ) {
                items(
                    items = dnsLogs,
                    key = { logGroup -> logGroup.firstOrNull()?.id ?: 0 }
                ) { logGroup ->
                    LogGroupCard(
                        logGroup = logGroup,
                        context = context,
                        navController = navController,
                        detailed = detailed,
                        onBlockedClick = onBlockedClick
                    )
                }
            }
        }
    }
}

@Composable
fun StatsSection(networkStats: com.buzzqw.bbdns.domain.model.NetworkStatsState) {
    com.buzzqw.bbdns.presentation.screens.stats.StatsContent(stats = networkStats)
}

@Composable
fun LogsContent(
    logs: List<List<Log>>,
    context: Context,
    navController: NavController,
    detailed: Boolean = false,
    onBlockedClick: ((Log) -> Unit)? = null
) {
    if (logs.isEmpty()) {
        MaterialPlaceholder(
            placeholderIcon = {
                Icon(
                    imageVector = Icons.Rounded.FileCopy,
                    contentDescription = null,
                    modifier = Modifier.scale(2f),
                    tint = MaterialTheme.colorScheme.outline
                )
            },
            placeholderText = stringResource(R.string.logs_no_logs)
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
        ) {
            items(logs) { logGroup ->
                if (logGroup.firstOrNull()?.packageID != 0) {
                    LogGroupCard(
                        logGroup = logGroup,
                        context = context,
                        navController = navController,
                        detailed = detailed,
                        onBlockedClick = onBlockedClick
                    )
                }
            }
        }
    }
}

@Composable
fun LogGroupCard(
    logGroup: List<Log>,
    context: Context,
    includeHeader: Boolean = true,
    navController: NavController,
    detailed: Boolean = false,
    onBlockedClick: ((Log) -> Unit)? = null
) {
    val application = context.uidToApplication(logGroup.first().packageID)
    val iconBitmap = application?.getApplicationIcon(
        context.packageManager,
        MaterialTheme.colorScheme.onSurface.toArgb(),
        context = context
    )?.toBitmap()?.asImageBitmap()
    val appName = application?.getApplicationName(context.packageManager)
        ?: stringResource(R.string.logs_unknown_app)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (includeHeader) {
                ApplicationHeader(
                    appName = appName,
                    appIcon = iconBitmap,
                    timestamp = logGroup.first().time.toFormattedDateTime()
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            LogDetails(logGroup, navController, detailed, onBlockedClick)
        }
    }
}

@Composable
fun ApplicationHeader(appName: String, appIcon: androidx.compose.ui.graphics.ImageBitmap?, timestamp: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        appIcon?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = appName,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        Text(
            text = timestamp,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun LogDetails(
    logs: List<Log>,
    navController: NavController,
    detailed: Boolean = false,
    onBlockedClick: ((Log) -> Unit)? = null
) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(10.dp))
    ) {
        logs.forEach { log ->
            val color = when (log.packetStatus) {
                FirewallResult.ACCEPT -> Color.Unspecified
                FirewallResult.DROP -> MaterialTheme.colorScheme.error
                FirewallResult.DNS_BLOCKED -> MaterialTheme.colorScheme.error
            }

            val explainBlock = onBlockedClick != null && log.canExplainBlock()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(
                        onClickLabel = stringResource(
                            if (explainBlock) R.string.block_reason_title else R.string.logs_open_details
                        )
                    ) {
                        if (explainBlock) {
                            onBlockedClick?.invoke(log)
                        } else {
                            navController.safeNavigate(LogRoutes.Packet.createRoute(log.id))
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 1.dp)
                        .weight(1f)
                        .heightIn(min = 30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        color = color,
                        text = (log.destinationPort.toIntOrNull()?.let { Port.getServiceName(it) } ?: log.destinationPort).uppercase(Locale.ROOT),
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        color = color,
                        text = if (log.destinationAddress != null) {
                            if (detailed) log.destinationAddress else extractBaseDomain(log.destinationAddress)
                        } else log.destinationIP,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .weight(2f)
                            .padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Icon(
                        tint = if (color != MaterialTheme.colorScheme.error) LocalContentColor.current else color,
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
            if (detailed) {
                Text(
                    text = stringResource(
                        R.string.logs_detailed_endpoint,
                        log.protocol,
                        log.sourceIP,
                        log.sourcePort,
                        log.destinationIP,
                        log.destinationPort
                    ),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 1.dp)
                )
            }
            Spacer(modifier = Modifier.height(1.dp))
        }
    }
}

fun extractBaseDomain(fullDomain: String): String {
    return try {
        val url = URL("http://$fullDomain")
        val host = url.host
        val parts = host.split(".")
        if (parts.size >= 2) {
            "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
        } else {
            host
        }
    } catch (e: Exception) {
        fullDomain
    }
}