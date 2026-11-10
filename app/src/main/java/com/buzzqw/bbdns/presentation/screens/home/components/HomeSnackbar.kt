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

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Snackbar host of the home scaffold, so cards and list rows deep in the
 * tree can show a short confirmation (with undo) instead of a toast.
 */
val LocalHomeSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }
