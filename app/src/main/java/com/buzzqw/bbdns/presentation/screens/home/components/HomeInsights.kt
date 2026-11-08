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

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material3.IconButton
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.NumberFormatter
import com.buzzqw.bbdns.presentation.theme.BbdnsShapes
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters

/**
 * Android "Private DNS" set to a specific host sends every query over DoT
 * straight to that provider, bypassing the VPN DNS and therefore BBDNS.
 * Returns the configured host, or null when Private DNS is off/automatic.
 */
fun strictPrivateDnsHost(context: Context): String? = try {
    val mode = Settings.Global.getString(context.contentResolver, "private_dns_mode")
    if (mode == "hostname") {
        Settings.Global.getString(context.contentResolver, "private_dns_specifier")
            ?.takeIf { it.isNotBlank() } ?: ""
    } else {
        null
    }
} catch (e: Exception) {
    null
}

@Composable
fun PrivateDnsWarningCard(host: String) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BbdnsShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_private_dns_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.home_private_dns_desc, host),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_WIRELESS_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            ) {
                Text(stringResource(R.string.home_private_dns_open_settings))
            }
        }
    }
}

/** Standard dashboard card: icon + title header, optional [trailing] control. */
@Composable
internal fun InsightCard(
    icon: ImageVector,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = BbdnsShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                trailing?.invoke()
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/** Stacked per-minute bars: allowed (brand colour) and blocked (error colour). */
@Composable
fun ActivityChartCard(details: FirewallCounters.SessionDetails, onClick: (() -> Unit)? = null) {
    val allowedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val blockedColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val totals = details.allowedPerMinute.zip(details.blockedPerMinute) { a, b -> a + b }
    val max = (totals.maxOrNull() ?: 0).coerceAtLeast(1)
    val summary = stringResource(
        R.string.home_activity_summary,
        details.blockedPerMinute.sum(),
        totals.sum(),
    )

    InsightCard(icon = Icons.Rounded.BarChart, title = stringResource(R.string.home_activity_title), onClick = onClick) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .semantics { contentDescription = summary }
        ) {
            val count = totals.size
            val gap = 2.dp.toPx()
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
            val radius = CornerRadius(barWidth / 3f, barWidth / 3f)
            totals.forEachIndexed { index, total ->
                val x = index * (barWidth + gap)
                // Faint track so empty minutes are still visible.
                drawRoundRect(trackColor, Offset(x, size.height - 2.dp.toPx()), Size(barWidth, 2.dp.toPx()), radius)
                if (total == 0) return@forEachIndexed
                val fullHeight = size.height * total / max
                val blockedHeight = fullHeight * details.blockedPerMinute[index] / total
                drawRoundRect(allowedColor, Offset(x, size.height - fullHeight), Size(barWidth, fullHeight), radius)
                if (blockedHeight > 0f) {
                    drawRoundRect(blockedColor, Offset(x, size.height - blockedHeight), Size(barWidth, blockedHeight), radius)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.home_activity_start, FirewallCounters.TIMELINE_MINUTES),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.home_activity_now),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun TopBlockedCard(top: List<Pair<String, Long>>, onDomainClick: (String) -> Unit) {
    InsightCard(icon = Icons.Rounded.Leaderboard, title = stringResource(R.string.home_top_blocked_title)) {
        if (top.isEmpty()) {
            EmptyInsight()
        } else {
            val max = top.first().second.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                top.forEach { (domain, count) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(BbdnsShapes.item)
                            .clickable(onClickLabel = stringResource(R.string.home_allow_domain_action)) { onDomainClick(domain) }
                            .padding(vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = domain,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = NumberFormatter.formatCount(count),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val fraction = count.toFloat() / max
                        val barColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                            val r = CornerRadius(size.height / 2, size.height / 2)
                            drawRoundRect(track, cornerRadius = r)
                            drawRoundRect(barColor, size = Size(size.width * fraction, size.height), cornerRadius = r)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentBlockedCard(
    recent: List<FirewallCounters.BlockedEvent>,
    now: Long,
    onDomainClick: (String) -> Unit,
) {
    InsightCard(icon = Icons.Rounded.History, title = stringResource(R.string.home_recent_blocked_title)) {
        if (recent.isEmpty()) {
            EmptyInsight()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                recent.forEach { event ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(BbdnsShapes.item)
                            .clickable(onClickLabel = stringResource(R.string.home_allow_domain_action)) { onDomainClick(event.domain) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = event.domain,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = DateUtils.getRelativeTimeSpanString(
                                event.timestamp,
                                now.coerceAtLeast(event.timestamp),
                                DateUtils.SECOND_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE,
                            ).toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyInsight() {
    Text(
        text = stringResource(R.string.home_insight_empty),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun AllowDomainDialog(domain: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_allow_domain_title)) },
        text = { Text(stringResource(R.string.home_allow_domain_desc, domain)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.home_allow_domain_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** Shown when Android revoked the VPN, usually because another VPN app started. */
@Composable
fun VpnRevokedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BbdnsShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VpnLock, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.home_vpn_revoked_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.home_vpn_revoked_desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** What keeps protection alive in the background, as Android allows it. */
data class StabilityStatus(val alwaysOnMissing: Boolean, val batteryRestricted: Boolean) {
    val needsAttention: Boolean get() = alwaysOnMissing || batteryRestricted
}

fun stabilityStatus(context: Context, alwaysOn: Boolean?): StabilityStatus {
    val batteryRestricted = try {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == false
    } catch (e: Exception) {
        false
    }
    // isAlwaysOn() is only known on Android 10+.
    val alwaysOnMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && alwaysOn == false
    return StabilityStatus(alwaysOnMissing, batteryRestricted)
}

@Composable
fun StabilityTipCard(status: StabilityStatus, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = BbdnsShapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HealthAndSafety, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.home_stability_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.home_stability_dismiss))
                }
            }
            Text(
                text = stringResource(R.string.home_stability_desc),
                modifier = Modifier.padding(end = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(modifier = Modifier.align(Alignment.End)) {
                if (status.batteryRestricted) {
                    TextButton(onClick = {
                        openSettings(context, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    }) { Text(stringResource(R.string.home_stability_battery)) }
                }
                if (status.alwaysOnMissing) {
                    TextButton(onClick = {
                        openSettings(context, Settings.ACTION_VPN_SETTINGS)
                    }) { Text(stringResource(R.string.home_stability_always_on)) }
                }
            }
        }
    }
}

private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
