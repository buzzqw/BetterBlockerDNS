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

package com.buzzqw.bbdns.core.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.buzzqw.bbdns.BuildConfig
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.service.firewall.utils.FirewallCounters
import com.buzzqw.bbdns.service.vpn.service.TunnelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Optional battery diagnostic log, off by default.
 *
 * While protection runs it writes one line per minute of *awake* time with
 * the battery state and what the app did since the previous line: CPU time
 * per thread, native event-loop wake-ups, packets, sessions, DNS/DoH work.
 * A line is also written on every screen, charger and Doze change, so each
 * interval has a single screen state. The ticker uses a plain coroutine
 * delay and no wake lock: it never wakes the device on its own, and the gap
 * between two lines with the screen off shows how long the CPU slept.
 *
 * The log stays on the device until the user exports it; it never contains
 * domains, IP addresses or app names. `scripts/analyze_battery_log.py`
 * summarizes an exported file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
object BatteryDiagnostics {
    private const val PREFS = "bbdns_battery_diagnostics"
    private const val KEY_ENABLED = "enabled"
    private const val SAMPLE_INTERVAL_MS = 60_000L
    private const val MAX_FILE_BYTES = 1_000_000L
    private const val FILE_NAME = "battery_diagnostics.log"
    private const val OLD_FILE_NAME = "battery_diagnostics.1.log"
    private const val TOP_THREADS = 6
    private const val FORMAT_VERSION = 1

    private val io = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + io)
    private var samplerJob: Job? = null
    private var appContext: Context? = null
    private var receiverRegistered = false
    @Volatile private var running = false
    @Volatile private var configSummary = ""

    // State of the interval that the next sample closes.
    private var last: Snapshot? = null
    private var screenOn = true
    private var charging = false
    private var doze = false

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            scope.launch {
                // Close the interval with the previous state, then switch.
                sample("change")
                when (action) {
                    Intent.ACTION_SCREEN_ON -> screenOn = true
                    Intent.ACTION_SCREEN_OFF -> screenOn = false
                    Intent.ACTION_POWER_CONNECTED -> charging = true
                    Intent.ACTION_POWER_DISCONNECTED -> charging = false
                    PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> doze = isDeviceIdle(context)
                }
                write("E", "event=${action.substringAfterLast('.').lowercase(Locale.US)}")
            }
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    /** Turn the log on or off from the settings; applies immediately. */
    fun setEnabled(context: Context, enabled: Boolean) {
        val ctx = context.applicationContext
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()
        appContext = ctx
        if (enabled) {
            if (running) startSampler(ctx)
        } else {
            scope.launch { write("E", "event=log_disabled") }
            stopSampler(ctx)
        }
    }

    /** Called by the VPN service once the tunnel runs. */
    fun onProtectionStarted(context: Context, config: String) {
        val ctx = context.applicationContext
        appContext = ctx
        running = true
        configSummary = config
        if (isEnabled(ctx)) startSampler(ctx)
    }

    fun onProtectionStopped(reason: String) {
        running = false
        val ctx = appContext ?: return
        if (samplerJob == null) return
        scope.launch {
            sample("stop")
            write("E", "event=vpn_stop reason=$reason")
        }
        stopSampler(ctx)
    }

    /** Record a one-off event (session reset, worker run…) when enabled. */
    fun event(name: String, detail: String = "") {
        if (samplerJob == null) return
        scope.launch { write("E", "event=$name" + if (detail.isNotEmpty()) " $detail" else "") }
    }

    fun hasLog(context: Context): Boolean = logSize(context) > 0

    fun logSize(context: Context): Long =
        listOf(OLD_FILE_NAME, FILE_NAME).sumOf { File(dir(context), it).length() }

    /** Copy the log (older part first) to [out], with a device header. */
    fun export(context: Context, out: OutputStream) {
        val header = buildString {
            append("# BBDNS battery diagnostics v$FORMAT_VERSION\n")
            append("# app=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) build=${BuildConfig.BUILD_TYPE}\n")
            append("# device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}\n")
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            append("# battery_optimization_ignored=${pm?.isIgnoringBatteryOptimizations(context.packageName)}\n")
            val exported = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
            append("# exported=$exported\n")
        }
        out.write(header.toByteArray())
        listOf(OLD_FILE_NAME, FILE_NAME).forEach { name ->
            val file = File(dir(context), name)
            if (file.exists()) file.inputStream().use { it.copyTo(out) }
        }
        out.flush()
    }

    fun clear(context: Context) {
        scope.launch {
            File(dir(context), OLD_FILE_NAME).delete()
            File(dir(context), FILE_NAME).delete()
            last = null
            if (samplerJob != null) write("E", "event=log_cleared $configSummary")
        }
    }

    private fun startSampler(ctx: Context) {
        if (samplerJob?.isActive == true) return
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        screenOn = pm?.isInteractive != false
        doze = isDeviceIdle(ctx)
        charging = readBattery(ctx).plugged
        registerReceiver(ctx)
        samplerJob = scope.launch {
            last = null
            write("E", "event=start $configSummary")
            sample("tick")
            while (isActive) {
                delay(SAMPLE_INTERVAL_MS)
                sample("tick")
            }
        }
    }

    private fun stopSampler(ctx: Context) {
        samplerJob?.cancel()
        samplerJob = null
        if (receiverRegistered) {
            try {
                ctx.unregisterReceiver(receiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
    }

    private fun registerReceiver(ctx: Context) {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private class Snapshot(
        val wall: Long,
        val elapsed: Long,
        val uptime: Long,
        val cpuMs: Long,
        val threads: Map<Int, Pair<String, Long>>,
        val native: LongArray?,
        val allowed: Long,
        val blocked: Long,
        val doh: Long,
        val dohCache: Long,
        val dohFail: Long,
        val rx: Long,
        val tx: Long,
    )

    private class Battery(
        val level: Int,
        val plugged: Boolean,
        val tempTenths: Int,
        val voltage: Int,
    )

    private fun takeSnapshot(): Snapshot {
        val (allowed, blocked) = FirewallCounters.snapshot()
        val uid = Process.myUid()
        return Snapshot(
            wall = System.currentTimeMillis(),
            elapsed = SystemClock.elapsedRealtime(),
            uptime = SystemClock.uptimeMillis(),
            cpuMs = Process.getElapsedCpuTime(),
            threads = readThreads(),
            native = TunnelManager.nativeStats(),
            allowed = allowed,
            blocked = blocked,
            doh = TunnelManager.dohRequests.get(),
            dohCache = TunnelManager.dohCacheHits.get(),
            dohFail = TunnelManager.dohFailures.get(),
            rx = TrafficStats.getUidRxBytes(uid),
            tx = TrafficStats.getUidTxBytes(uid),
        )
    }

    /**
     * One line describing the interval since the previous snapshot: deltas
     * for counters, current values for the battery and the session gauges.
     */
    private fun sample(kind: String) {
        val ctx = appContext ?: return
        try {
            val now = takeSnapshot()
            val prev = last
            last = now
            val battery = readBattery(ctx)
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val sb = StringBuilder()
            sb.append("kind=").append(kind)
            sb.append(" scr=").append(if (screenOn) 1 else 0)
            sb.append(" chg=").append(if (charging) 1 else 0)
            sb.append(" doze=").append(if (doze) 1 else 0)
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            sb.append(" psave=").append(if (pm?.isPowerSaveMode == true) 1 else 0)
            sb.append(" bat=").append(battery.level)
            sb.append(" temp=").append(battery.tempTenths)
            sb.append(" mv=").append(battery.voltage)
            bm?.let {
                sb.append(" uah=").append(it.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
                sb.append(" ua=").append(it.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
            }
            sb.append(" net=").append(networkType(ctx))
            if (prev != null) {
                sb.append(" dt=").append(now.elapsed - prev.elapsed)
                sb.append(" awake=").append(now.uptime - prev.uptime)
                sb.append(" cpu=").append(now.cpuMs - prev.cpuMs)
                sb.append(" thr=").append(threadDeltas(prev.threads, now.threads))
                val n = now.native
                val p = prev.native
                if (n != null && p != null && n.size >= 13 && p.size >= 13) {
                    sb.append(" loops=").append(n[0] - p[0])
                    sb.append(" rck=").append(n[1] - p[1])
                    sb.append(" idle=").append(n[2] - p[2])
                    sb.append(" tin=").append(n[3] - p[3])
                    sb.append(" tinb=").append(n[4] - p[4])
                    sb.append(" tout=").append(n[5] - p[5])
                    sb.append(" toutb=").append(n[6] - p[6])
                    sb.append(" sock=").append(n[7] - p[7])
                    sb.append(" jni=").append(n[8] - p[8])
                    sb.append(" zwin=").append(n[9] - p[9])
                }
                sb.append(" allow=").append(now.allowed - prev.allowed)
                sb.append(" block=").append(now.blocked - prev.blocked)
                sb.append(" doh=").append(now.doh - prev.doh)
                sb.append(" dohc=").append(now.dohCache - prev.dohCache)
                sb.append(" dohf=").append(now.dohFail - prev.dohFail)
                if (now.rx >= 0 && prev.rx >= 0) sb.append(" rx=").append(now.rx - prev.rx)
                if (now.tx >= 0 && prev.tx >= 0) sb.append(" tx=").append(now.tx - prev.tx)
            }
            now.native?.takeIf { it.size >= 13 }?.let {
                sb.append(" tcp=").append(it[10])
                sb.append(" udp=").append(it[11])
                sb.append(" icmp=").append(it[12])
            }
            write("S", sb.toString())
        } catch (e: Exception) {
            Logger.warn("Battery diagnostics sample failed: ${e.message}")
        }
    }

    /** CPU ticks per thread of this process, from /proc/self/task. */
    private fun readThreads(): Map<Int, Pair<String, Long>> {
        val result = HashMap<Int, Pair<String, Long>>()
        val tasks = File("/proc/self/task").listFiles() ?: return result
        for (task in tasks) {
            val tid = task.name.toIntOrNull() ?: continue
            try {
                val stat = File(task, "stat").readText()
                val open = stat.indexOf('(')
                val close = stat.lastIndexOf(')')
                if (open < 0 || close < open) continue
                val name = stat.substring(open + 1, close)
                val fields = stat.substring(close + 2).split(' ')
                // After the name: state(0) … utime(11) stime(12), in clock ticks.
                val ticks = fields[11].toLong() + fields[12].toLong()
                result[tid] = name to ticks
            } catch (_: Exception) {
            }
        }
        return result
    }

    /** "name:ms,name:ms" for the busiest thread groups of the interval. */
    private fun threadDeltas(
        prev: Map<Int, Pair<String, Long>>,
        now: Map<Int, Pair<String, Long>>,
    ): String {
        val byName = HashMap<String, Long>()
        for ((tid, value) in now) {
            val before = prev[tid]?.second ?: 0L
            val delta = value.second - before
            if (delta <= 0) continue
            // Pool threads differ by a number only: group them.
            val group = value.first.trimEnd { it.isDigit() || it == '-' || it == '#' }
                .ifEmpty { value.first }
                .replace(' ', '_')
                .replace(',', '_')
                .replace(':', '_')
            byName[group] = (byName[group] ?: 0L) + delta
        }
        if (byName.isEmpty()) return "-"
        // Linux USER_HZ is 100 on Android: one tick is 10 ms.
        return byName.entries.sortedByDescending { it.value }
            .take(TOP_THREADS)
            .joinToString(",") { "${it.key}:${it.value * 10}" }
    }

    private fun readBattery(ctx: Context): Battery {
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plugged = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        return Battery(
            level = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            plugged = plugged,
            tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0,
            voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
        )
    }

    private fun isDeviceIdle(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isDeviceIdleMode
    }

    private fun networkType(ctx: Context): String {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "?"
        // With the VPN up the default network is the VPN itself; look at the
        // physical networks instead.
        @Suppress("DEPRECATION")
        val caps = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }
            .filter { !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
        val types = buildList {
            if (caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }) add("wifi")
            if (caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }) add("cell")
            if (caps.any { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) }) add("eth")
        }
        return if (types.isEmpty()) "none" else types.joinToString("+")
    }

    private fun dir(context: Context): File =
        File(context.filesDir, "diagnostics").apply { mkdirs() }

    private fun write(type: String, line: String) {
        val ctx = appContext ?: return
        try {
            val file = File(dir(ctx), FILE_NAME)
            if (file.length() > MAX_FILE_BYTES) {
                val old = File(dir(ctx), OLD_FILE_NAME)
                old.delete()
                file.renameTo(old)
            }
            FileOutputStream(file, true).use {
                it.write("${timeFormat.format(Date())} $type $line\n".toByteArray())
            }
        } catch (e: Exception) {
            Logger.warn("Battery diagnostics write failed: ${e.message}")
        }
    }
}
