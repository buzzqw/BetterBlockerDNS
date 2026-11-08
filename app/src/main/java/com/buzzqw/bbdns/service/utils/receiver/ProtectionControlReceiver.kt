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

package com.buzzqw.bbdns.service.utils.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buzzqw.bbdns.core.logging.Logger
import com.buzzqw.bbdns.service.utils.manager.ProtectionPause
import com.buzzqw.bbdns.service.utils.manager.ProtectionState
import com.buzzqw.bbdns.service.utils.manager.VpnManager
import com.buzzqw.bbdns.service.vpn.service.VpnConnectionServer

/**
 * Lets automation apps (Tasker, MacroDroid, Home Assistant, adb) start, stop,
 * pause and resume protection without opening BBDNS.
 *
 * The receiver only changes the protection state: it never exposes logs,
 * settings or blocked domains, so it is safe to keep it exported.
 */
class ProtectionControlReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_START = "com.buzzqw.bbdns.action.START_PROTECTION"
        const val ACTION_STOP = "com.buzzqw.bbdns.action.STOP_PROTECTION"
        const val ACTION_TOGGLE = "com.buzzqw.bbdns.action.TOGGLE_PROTECTION"

        /** Default pause used when the extra is missing or invalid. */
        private const val DEFAULT_PAUSE_MINUTES = 15
        private const val MAX_PAUSE_MINUTES = 24 * 60
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Logger.info("Automation action: $action")
        try {
            when (action) {
                ACTION_START -> startProtection(context)
                ACTION_STOP -> VpnManager.stop(context)
                ACTION_TOGGLE -> if (ProtectionState.isRunning) {
                    VpnManager.stop(context)
                } else {
                    startProtection(context)
                }
                ProtectionPause.ACTION_PAUSE -> pause(context, intent)
                ProtectionPause.ACTION_RESUME -> resume(context)
                else -> Logger.warn("Unknown automation action: $action")
            }
        } catch (e: Exception) {
            // Android 12+ can refuse a background foreground-service start.
            Logger.error("Automation action $action failed: ${e.message}", e)
        }
    }

    private fun startProtection(context: Context) {
        if (!VpnManager.permissionChecker(context)) {
            Logger.warn("Automation start ignored: VPN permission not granted")
            return
        }
        VpnManager.start(context)
    }

    private fun pause(context: Context, intent: Intent) {
        if (!ProtectionState.isRunning) {
            Logger.warn("Automation pause ignored: protection is not running")
            return
        }
        val minutes = intent.getIntExtra(ProtectionPause.EXTRA_MINUTES, DEFAULT_PAUSE_MINUTES)
            .coerceIn(1, MAX_PAUSE_MINUTES)
        val pauseIntent = Intent(context, VpnConnectionServer::class.java).apply {
            action = ProtectionPause.ACTION_PAUSE
            putExtra(ProtectionPause.EXTRA_MINUTES, minutes)
        }
        context.startService(pauseIntent)
    }

    private fun resume(context: Context) {
        if (ProtectionState.isRunning) {
            val resumeIntent = Intent(context, VpnConnectionServer::class.java).apply {
                action = ProtectionPause.ACTION_RESUME
            }
            context.startService(resumeIntent)
        } else {
            // Nothing is running: drop a stale saved pause.
            ProtectionPause.clear(context)
        }
    }
}
