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

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.NumberFormatter
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters
import kotlin.math.roundToInt

private val HISTORY_RANGES = listOf(7, FirewallCounters.HISTORY_DAYS)

/**
 * Daily allowed/blocked totals of the last 7 or 30 days, kept by
 * [FirewallCounters] across app restarts. Same visual language as
 * [ActivityChartCard]: stacked bars, blocked on top in the error colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryCard(history: List<FirewallCounters.DayStats>) {
    var days by rememberSaveable { mutableIntStateOf(HISTORY_RANGES.first()) }
    val shown = history.takeLast(days)

    InsightCard(
        icon = Icons.Rounded.DateRange,
        title = stringResource(R.string.home_history_title),
        trailing = {
            SingleChoiceSegmentedButtonRow {
                HISTORY_RANGES.forEachIndexed { index, range ->
                    SegmentedButton(
                        selected = days == range,
                        onClick = { days = range },
                        shape = SegmentedButtonDefaults.itemShape(index, HISTORY_RANGES.size),
                        icon = {},
                        label = {
                            Text(
                                text = stringResource(R.string.home_history_days, range),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
        },
    ) {
        val blocked = shown.sumOf { it.blocked }
        val total = shown.sumOf { it.total }
        if (total == 0L) {
            Text(
                text = stringResource(R.string.home_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HistoryContent(shown = shown, days = days, blocked = blocked, total = total)
        }
    }
}

@Composable
private fun HistoryContent(
    shown: List<FirewallCounters.DayStats>,
    days: Int,
    blocked: Long,
    total: Long,
) {
    Column {
        val summary = stringResource(
            R.string.home_history_summary,
            NumberFormatter.formatCount(blocked),
            NumberFormatter.formatCount(total),
            days,
        )
        HistoryBars(
            days = shown,
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics { contentDescription = summary },
        )
        Spacer(modifier = Modifier.height(6.dp))
        val context = LocalContext.current
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = shown.firstOrNull()?.let {
                    DateUtils.formatDateTime(
                        context,
                        it.dayStart,
                        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_MONTH,
                    )
                }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_history_today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HistoryTotal(
                label = stringResource(R.string.home_total_requests),
                value = NumberFormatter.formatCount(total),
                modifier = Modifier.weight(1f),
            )
            HistoryTotal(
                label = stringResource(R.string.home_blocked_requests),
                value = NumberFormatter.formatCount(blocked),
                modifier = Modifier.weight(1f),
            )
            HistoryTotal(
                label = stringResource(R.string.home_block_rate),
                value = stringResource(
                    R.string.home_block_rate_value,
                    (blocked * 100.0 / total).roundToInt(),
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HistoryBars(days: List<FirewallCounters.DayStats>, modifier: Modifier = Modifier) {
    val allowedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val blockedColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val max = (days.maxOfOrNull { it.total } ?: 0L).coerceAtLeast(1L)

    Canvas(modifier = modifier) {
        val count = days.size.coerceAtLeast(1)
        val gap = (if (count <= 7) 8.dp else 3.dp).toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val radius = CornerRadius(
            (barWidth / 3f).coerceAtMost(6.dp.toPx()),
            (barWidth / 3f).coerceAtMost(6.dp.toPx()),
        )
        days.forEachIndexed { index, day ->
            val x = index * (barWidth + gap)
            // Faint track so days without traffic are still visible.
            drawRoundRect(trackColor, Offset(x, size.height - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()), radius)
            if (day.total == 0L) return@forEachIndexed
            val fullHeight = size.height * day.total / max
            val blockedHeight = fullHeight * day.blocked / day.total
            drawRoundRect(allowedColor, Offset(x, size.height - fullHeight), Size(barWidth, fullHeight), radius)
            if (blockedHeight > 0f) {
                drawRoundRect(blockedColor, Offset(x, size.height - blockedHeight), Size(barWidth, blockedHeight), radius)
            }
        }
    }
}

@Composable
private fun HistoryTotal(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
