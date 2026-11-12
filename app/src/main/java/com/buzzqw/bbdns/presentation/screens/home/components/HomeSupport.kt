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
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.buzzqw.bbdns.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Donation links: the single source of truth for the home support card. */
private const val PAYPAL_URL =
    "https://www.paypal.com/donate/?business=azanzani%40gmail.com&item_name=BetterBlockerDNS"
private const val KOFI_URL = "https://ko-fi.com/buzzqw"

/**
 * One-shot snapshot for the resource card: CPU of the BBDNS process, battery
 * level and draw of the whole phone.
 */
data class ResourceUsage(
    val cpuPercent: Float? = null,
    val batteryCurrentMa: Float? = null,
    val batteryLevel: Int? = null,
    val charging: Boolean = false,
)

/**
 * Donation card at the bottom of the dashboard. PayPal and Ko-fi open in the
 * browser (or in their own app when installed).
 */
@Composable
fun SupportCard() {
    val context = LocalContext.current

    InsightCard(
        icon = Icons.Rounded.Favorite,
        title = stringResource(R.string.home_support_title),
    ) {
        Text(
            text = stringResource(R.string.home_support_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { openUrl(context, PAYPAL_URL) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.home_support_paypal))
            }
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = { openUrl(context, KOFI_URL) },
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalCafe,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.home_support_kofi))
            }
        }
    }
}

/** How often the live CPU and battery values are refreshed while Home is open. */
private const val RESOURCE_REFRESH_MS = 2_000L

/**
 * Live CPU and battery snapshot, refreshed only while the dashboard is composed.
 *
 * CPU is read from `/proc/self/stat` twice, about a second apart, so the value
 * reflects the app process (VPN service included) while the home is open. The
 * battery draw and level come from the framework and are refreshed together
 * with the CPU. Nothing keeps running after the card leaves the screen.
 */
@Composable
fun ResourceUsageCard() {
    // Collapsed by default: the CPU/battery sampling runs only while the
    // values are actually shown.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val toggle = { expanded = !expanded }
    InsightCard(
        icon = Icons.Rounded.Memory,
        title = stringResource(R.string.home_resources_title),
        trailing = {
            TextButton(onClick = toggle) {
                Text(
                    stringResource(
                        if (expanded) R.string.home_more_hide else R.string.home_more_show
                    )
                )
            }
        },
        onClick = toggle,
    ) {
        if (expanded) {
            ResourceUsageValues()
        } else {
            Text(
                text = stringResource(R.string.home_resources_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResourceUsageValues() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val usage by produceState(initialValue = ResourceUsage(), context, lifecycle) {
        // The composition survives while the app is in the background, so
        // sample only while the screen is actually visible.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val battery = withContext(Dispatchers.IO) { readBattery(context) }
                value = battery.copy(cpuPercent = sampleCpuPercent())
                delay(RESOURCE_REFRESH_MS)
            }
        }
    }

    // The framework only exposes the whole phone's battery: show the level
    // left and the phone's draw, labelled as such, never as BBDNS usage.
    val batteryLevel = usage.batteryLevel
    val batteryValue = when {
        usage.charging -> batteryLevel?.let {
            stringResource(R.string.home_resources_charging_level, it)
        } ?: stringResource(R.string.home_resources_charging)
        batteryLevel != null -> stringResource(R.string.home_resources_battery_level_value, batteryLevel)
        else -> stringResource(R.string.home_resources_unavailable)
    }
    val batteryDetail = usage.batteryCurrentMa?.takeIf { !usage.charging }?.let {
        stringResource(R.string.home_resources_battery_hint_current, it)
    } ?: stringResource(R.string.home_resources_battery_hint)

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UsageCell(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Memory,
                label = stringResource(R.string.home_resources_cpu),
                value = usage.cpuPercent?.let {
                    stringResource(R.string.home_resources_cpu_value, it)
                } ?: stringResource(R.string.home_resources_unavailable),
                detail = stringResource(R.string.home_resources_cpu_hint),
            )
            UsageCell(
                modifier = Modifier.weight(1f),
                icon = if (usage.charging) {
                    Icons.Rounded.BatteryChargingFull
                } else {
                    Icons.Rounded.BatteryFull
                },
                label = stringResource(R.string.home_resources_battery),
                value = batteryValue,
                detail = batteryDetail,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_resources_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UsageCell(
    icon: ImageVector,
    label: String,
    value: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
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
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Process CPU share over a short window, normalised by the core count so the
 * result is a 0-100 percentage of the whole device. Two /proc reads only.
 */
private suspend fun sampleCpuPercent(): Float? = withContext(Dispatchers.IO) {
    val clockTicks = try {
        Os.sysconf(OsConstants._SC_CLK_TCK)
    } catch (_: Exception) {
        100L
    }
    if (clockTicks <= 0L) return@withContext null

    val startTicks = readProcessCpuTicks() ?: return@withContext null
    val startWall = SystemClock.elapsedRealtime()
    delay(1_200L)
    val endTicks = readProcessCpuTicks() ?: return@withContext null
    val endWall = SystemClock.elapsedRealtime()

    val wallMs = (endWall - startWall).coerceAtLeast(1L)
    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    val cpuMs = (endTicks - startTicks).coerceAtLeast(0L) * 1000.0 / clockTicks
    ((cpuMs / (wallMs.toDouble() * cores)) * 100.0)
        .toFloat()
        .coerceIn(0f, 100f)
}

/** utime + stime (fields 14 and 15) from /proc/self/stat, in clock ticks. */
private fun readProcessCpuTicks(): Long? = try {
    val stat = File("/proc/self/stat").readText()
    // The process name is in parentheses and may contain spaces: start after it.
    val fields = stat.substringAfter(')').trim().split(' ')
    if (fields.size > 12) fields[11].toLong() + fields[12].toLong() else null
} catch (_: Exception) {
    null
}

private fun readBattery(context: Context): ResourceUsage = try {
    val batteryManager = context.getSystemService(BatteryManager::class.java)
    val level = batteryManager
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }
    val rawCurrent = batteryManager
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        ?: Int.MIN_VALUE
    val currentMa = if (rawCurrent != Int.MIN_VALUE && rawCurrent != 0) {
        abs(rawCurrent) / 1000f
    } else {
        null
    }
    val status = context
        .registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    ResourceUsage(
        batteryCurrentMa = currentMa,
        batteryLevel = level,
        charging = charging,
    )
} catch (_: Exception) {
    ResourceUsage()
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        Toast.makeText(context, R.string.home_support_no_browser, Toast.LENGTH_SHORT).show()
    }
}
