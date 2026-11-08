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

package com.buzzqw.bbdns.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The single source of corner radii in BBDNS, so every screen looks the same:
 * - [group]: a container of stacked settings rows;
 * - [item]: a row inside a group (small radius, rows sit 2dp apart);
 * - [card]: a standalone card (home dashboard, dialogs, banners);
 * - [chip]: small pills and progress tracks.
 */
object BbdnsShapes {
    val group = RoundedCornerShape(24.dp)
    val item = RoundedCornerShape(8.dp)
    val card = RoundedCornerShape(20.dp)
    val chip = RoundedCornerShape(50)
}
