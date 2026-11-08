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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.NumberFormatter
import com.buzzqw.bbdns.presentation.theme.BbdnsShapes

/** "1.2 MB/s" style speed label. */
fun formatSpeed(bytesPerSecond: Float): String =
    NumberFormatter.formatBytes(bytesPerSecond.toLong()) + "/s"

/**
 * Compact live card: device traffic speed (line, last 2 minutes) next to the
 * blocked requests per minute (bars, last 30 minutes).
 */
@Composable
fun LiveTrafficCard(
    traffic: TrafficSampler.Snapshot,
    blockedPerMinute: List<Int>,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.tertiary
    val barColor = MaterialTheme.colorScheme.error
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val speedText = stringResource(
        R.string.stats_live_speed_value,
        formatSpeed(traffic.currentDownload),
        formatSpeed(traffic.currentUpload),
    )
    val blockedLastMinutes = blockedPerMinute.takeLast(5).sum()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = BbdnsShapes.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1.4f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Speed, null, tint = lineColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stats_live_speed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = speedText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Sparkline(
                    primary = traffic.download,
                    secondary = traffic.upload,
                    primaryColor = lineColor,
                    secondaryColor = upColor,
                    trackColor = track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .semantics { contentDescription = speedText },
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stats_live_blocks),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.stats_live_blocks_value, blockedLastMinutes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(6.dp))
                MiniBars(
                    values = blockedPerMinute,
                    color = barColor,
                    trackColor = track,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                )
            }
        }
    }
}

/** Two-series line chart scaled to the common maximum. */
@Composable
fun Sparkline(
    primary: List<Float>,
    secondary: List<Float>,
    primaryColor: Color,
    secondaryColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        drawLine(trackColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 2f)
        val max = maxOf(primary.maxOrNull() ?: 0f, secondary.maxOrNull() ?: 0f).coerceAtLeast(1f)
        fun line(values: List<Float>, color: Color, width: Float) {
            if (values.size < 2) return
            val step = size.width / (TrafficSampler.POINTS - 1)
            val start = size.width - step * (values.size - 1)
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = start + i * step
                val y = size.height - (v / max) * (size.height - 3f) - 1.5f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        line(secondary, secondaryColor.copy(alpha = 0.7f), 1.5.dp.toPx())
        line(primary, primaryColor, 2.dp.toPx())
    }
}

@Composable
fun MiniBars(values: List<Int>, color: Color, trackColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val count = values.size.coerceAtLeast(1)
        val gap = 1.5.dp.toPx()
        val w = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val r = CornerRadius(w / 3f, w / 3f)
        values.forEachIndexed { i, v ->
            val x = i * (w + gap)
            drawRoundRect(trackColor, Offset(x, size.height - 2f), Size(w, 2f), r)
            if (v > 0) {
                val h = size.height * v / max
                drawRoundRect(color, Offset(x, size.height - h), Size(w, h), r)
            }
        }
    }
}

/** Small labelled number used in the statistics grids. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** One app row of the "apps with most blocks" card. */
data class AppUsage(val uid: Int, val queries: Long, val blocked: Long)

@Composable
fun TopAppsList(apps: List<AppUsage>) {
    val context = LocalContext.current
    val pm = context.packageManager
    if (apps.isEmpty()) {
        Text(
            text = stringResource(R.string.home_insight_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val max = apps.maxOf { it.queries }.coerceAtLeast(1)
    val unknown = stringResource(R.string.logs_unknown_app)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        apps.forEach { app ->
            val info = remember(app.uid) {
                try {
                    val pkg = pm.getPackagesForUid(app.uid)?.firstOrNull()
                    val ai = pkg?.let { pm.getApplicationInfo(it, 0) }
                    val label = ai?.let { pm.getApplicationLabel(it).toString() } ?: pkg
                    val icon = ai?.let { pm.getApplicationIcon(it).toBitmap(64, 64).asImageBitmap() }
                    label to icon
                } catch (_: Exception) {
                    null to null
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.second != null) {
                    Image(bitmap = info.second!!, contentDescription = null, modifier = Modifier.size(28.dp))
                } else {
                    Icon(Icons.Rounded.Apps, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.outline)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = info.first ?: unknown,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(
                                R.string.stats_app_counts,
                                NumberFormatter.formatCount(app.blocked),
                                NumberFormatter.formatCount(app.queries),
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val allowedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    val blockedColor = MaterialTheme.colorScheme.error
                    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    Canvas(modifier = Modifier.fillMaxWidth().height(5.dp)) {
                        val r = CornerRadius(size.height / 2, size.height / 2)
                        drawRoundRect(track, cornerRadius = r)
                        val full = size.width * app.queries / max
                        drawRoundRect(allowedColor, size = Size(full, size.height), cornerRadius = r)
                        if (app.queries > 0 && app.blocked > 0) {
                            drawRoundRect(blockedColor, size = Size(full * app.blocked / app.queries, size.height), cornerRadius = r)
                        }
                    }
                }
            }
        }
    }
}

/** Tappable row "label ............ value >" used for links between pages. */
@Composable
fun LinkRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
