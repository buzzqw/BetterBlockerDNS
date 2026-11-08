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

package com.buzzqw.bbdns.core.utils

import android.os.Build

/**
 * Best-effort detection of an AMOLED/OLED panel.
 *
 * Android does not expose the panel technology through a public API. Several
 * vendors publish it through system properties or the model/device name, so
 * this is a heuristic used only to choose a sensible first-run default: the
 * user can always change it in Colours & Styles.
 */
object DeviceDisplay {

    private val PROPERTY_KEYS = listOf(
        "ro.build.characteristics",
        "ro.config.screen",
        "ro.config.display",
        "ro.product.model",
        "ro.product.device",
        "ro.hardware",
    )

    fun isProbablyAmoled(): Boolean {
        val haystack = buildString {
            PROPERTY_KEYS.forEach { key ->
                systemProperty(key)?.let { append(' ').append(it) }
            }
            append(' ').append(Build.MANUFACTURER)
            append(' ').append(Build.MODEL)
            append(' ').append(Build.DEVICE)
        }.lowercase()
        return haystack.contains("amoled") || haystack.contains("oled")
    }

    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        get.invoke(null, key) as? String
    }.getOrNull()
}
