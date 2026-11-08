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

import android.net.TrafficStats
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/**
 * Device-wide download/upload speed, sampled only while a screen showing it
 * is visible (no background polling, so no battery cost when closed).
 */
object TrafficSampler {
    const val POINTS = 60
    private const val MIN_INTERVAL_MS = 1_000L

    private val lock = Any()
    private val down = ArrayDeque<Float>()
    private val up = ArrayDeque<Float>()
    private var lastRx = -1L
    private var lastTx = -1L
    private var lastTime = 0L

    data class Snapshot(val download: List<Float>, val upload: List<Float>) {
        val currentDownload: Float get() = download.lastOrNull() ?: 0f
        val currentUpload: Float get() = upload.lastOrNull() ?: 0f
        val peak: Float get() = maxOf(download.maxOrNull() ?: 0f, upload.maxOrNull() ?: 0f)
    }

    /** Take a sample if at least a second passed since the previous one. */
    fun sample(now: Long = System.currentTimeMillis()) {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx < 0 || tx < 0) return // TrafficStats.UNSUPPORTED
        synchronized(lock) {
            if (now - lastTime < MIN_INTERVAL_MS) return
            if (lastRx >= 0 && lastTime > 0) {
                val seconds = (now - lastTime) / 1000f
                // A gap (screen closed) would average a long period: restart the line.
                if (seconds > 30f) {
                    down.clear(); up.clear()
                } else {
                    push(down, ((rx - lastRx).coerceAtLeast(0)) / seconds)
                    push(up, ((tx - lastTx).coerceAtLeast(0)) / seconds)
                }
            }
            lastRx = rx
            lastTx = tx
            lastTime = now
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) { Snapshot(down.toList(), up.toList()) }

    private fun push(queue: ArrayDeque<Float>, value: Float) {
        queue.addLast(value)
        while (queue.size > POINTS) queue.removeFirst()
    }
}

/**
 * Wall-clock tick that advances every [intervalMs] only while the screen is
 * at least STARTED, and samples [TrafficSampler] on each tick. Screens use it
 * as a `remember` key to refresh their live numbers.
 */
@Composable
fun rememberLiveTick(intervalMs: Long = 2_000L): Long {
    val owner = LocalLifecycleOwner.current
    val tick = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(owner, intervalMs) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                TrafficSampler.sample()
                tick.longValue = System.currentTimeMillis()
                delay(intervalMs)
            }
        }
    }
    return tick.longValue
}
