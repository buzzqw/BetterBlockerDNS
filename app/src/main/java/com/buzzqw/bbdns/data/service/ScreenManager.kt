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

package com.buzzqw.bbdns.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.buzzqw.bbdns.service.firewall.utils.SessionInvalidator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton

@AndroidEntryPoint
class ScreenStateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var screenStateManager: ScreenStateManager

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                screenStateManager.updateScreenStatus(ScreenManager.SCREEN_OFF)
            }
            Intent.ACTION_SCREEN_ON -> {
                screenStateManager.updateScreenStatus(ScreenManager.SCREEN_ON)
            }
        }
    }
}

@Singleton
class ScreenStateManager
 {
    @Volatile
    var currentScreenStatus: ScreenManager = ScreenManager.SCREEN_ON

    /** Set by ScreenRule: true when some traffic is blocked with the screen off. */
    @Volatile
    var blockWhenScreenOff: Boolean = false

    fun updateScreenStatus(screenStatus: ScreenManager) {
        val turnedOff = screenStatus == ScreenManager.SCREEN_OFF &&
            currentScreenStatus != ScreenManager.SCREEN_OFF
        currentScreenStatus = screenStatus
        // Only new flows reach the rules: end the open ones so the screen-off
        // block also applies to them. Without the setting nothing is dropped,
        // so connections (push, music) survive and do not reconnect.
        if (turnedOff && blockWhenScreenOff) SessionInvalidator.invalidate()
    }
}

enum class ScreenManager {
    SCREEN_OFF,
    SCREEN_ON
}