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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.presentation.theme.BbdnsShapes
import kotlin.math.roundToInt

/** Report the bounds of a composable in window coordinates (for [HomeOnboardingOverlay]). */
fun Modifier.onBoundsInWindow(onBounds: (Rect) -> Unit): Modifier =
    onGloballyPositioned { coordinates -> onBounds(coordinates.boundsInWindow()) }

/**
 * First-run hint: dims the screen, cuts a hole around the power button
 * ([target], window coordinates) and explains how to turn protection on.
 * Tapping the hole starts protection; "Skip" just closes the hint.
 */
@Composable
fun HomeOnboardingOverlay(
    target: Rect,
    onPowerClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)
    val ring = MaterialTheme.colorScheme.primary
    var origin by remember { mutableStateOf(Offset.Zero) }
    val hasTarget = target.width > 0f && target.height > 0f
    // Target in the overlay's own coordinates.
    val center = target.center - origin
    val radius = target.minDimension / 2f + with(density) { 8.dp.toPx() }
    val powerDescription = stringResource(R.string.home_toggle_protection)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    // Absorb taps outside the highlighted button.
                }
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            drawRect(color = scrim, size = size)
            if (hasTarget) {
                drawCircle(color = Color.Transparent, radius = radius, center = center, blendMode = BlendMode.Clear)
                drawCircle(
                    color = ring,
                    radius = radius + 4.dp.toPx(),
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }

        if (hasTarget) {
            val sizeDp = with(density) { (radius * 2f).toDp() }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - radius).roundToInt(),
                            (center.y - radius).roundToInt(),
                        )
                    }
                    .size(sizeDp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) {
                        onPowerClick()
                        onDismiss()
                    }
                    .semantics { contentDescription = powerDescription }
            )
        }

        // The hint card sits below the button (or centred without a target).
        val cardTop = if (hasTarget) center.y + radius + with(density) { 24.dp.toPx() } else 0f
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hasTarget) {
                        Modifier.offset { IntOffset(0, cardTop.roundToInt()) }
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .padding(horizontal = 32.dp),
            contentAlignment = if (hasTarget) Alignment.TopCenter else Alignment.Center,
        ) {
            Card(
                modifier = Modifier.widthIn(max = 360.dp),
                shape = BbdnsShapes.card,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            ) {
                Column(modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 12.dp, bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.home_onboarding_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.home_onboarding_desc),
                        modifier = Modifier.padding(top = 6.dp, end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.home_onboarding_skip))
                    }
                }
            }
        }
    }
}
