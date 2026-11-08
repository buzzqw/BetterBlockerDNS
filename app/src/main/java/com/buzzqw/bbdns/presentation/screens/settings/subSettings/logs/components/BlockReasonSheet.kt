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

package com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.BlockMatch
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.BlockMatchType
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.BlockReason
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.dns.hosts.BlockRuleSource
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs.viewModel.BlockReasonUiState
import com.buzzqw.bbdns.presentation.screens.settings.subSettings.logs.viewModel.BlockReasonViewModel
import com.buzzqw.bbdns.presentation.theme.BbdnsShapes

/**
 * "Why is this blocked?" bottom sheet: explains which rules and lists block
 * [domain] and lets the user allow or copy it.
 *
 * @param onOpenDetails optional action to open the full packet details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockReasonSheet(
    domain: String,
    onDismiss: () -> Unit,
    onOpenDetails: (() -> Unit)? = null,
    viewModel: BlockReasonViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(domain) { viewModel.load(domain) }

    val dismiss = {
        viewModel.clear()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetHeader(domain = domain)

            when (val current = state) {
                is BlockReasonUiState.Idle,
                is BlockReasonUiState.Searching -> SearchingCard(current as? BlockReasonUiState.Searching)
                is BlockReasonUiState.Failed -> StatusCard(
                    icon = Icons.Rounded.Info,
                    text = stringResource(R.string.block_reason_failed),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
                is BlockReasonUiState.Ready -> ReasonContent(current.reason, current.allowedNow)
            }

            val ready = state as? BlockReasonUiState.Ready
            val canAllow = ready != null && !ready.allowedNow && ready.reason.isCurrentlyBlocked
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.allowDomain(domain) },
                    enabled = canAllow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.block_reason_allow))
                }
                OutlinedButton(
                    onClick = { copyDomain(context, domain) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.block_reason_copy))
                }
                if (onOpenDetails != null) {
                    TextButton(
                        onClick = {
                            dismiss()
                            onOpenDetails()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.block_reason_packet_details))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(domain: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Block,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = stringResource(R.string.block_reason_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            SelectionContainer {
                Text(
                    text = domain,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchingCard(state: BlockReasonUiState.Searching?) {
    Surface(
        shape = BbdnsShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (state != null && state.total > 0) {
                        stringResource(R.string.block_reason_searching_progress, state.scanned, state.total)
                    } else {
                        stringResource(R.string.block_reason_searching)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state != null && state.total > 0) {
                LinearProgressIndicator(
                    progress = { state.scanned.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ReasonContent(reason: BlockReason, allowedNow: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when {
            allowedNow -> StatusCard(
                icon = Icons.Rounded.CheckCircle,
                text = stringResource(R.string.block_reason_status_allowed_now),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            !reason.isCurrentlyBlocked && reason.allowedBy.isNotEmpty() -> StatusCard(
                icon = Icons.Rounded.CheckCircle,
                text = stringResource(R.string.block_reason_status_allowed),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            !reason.isCurrentlyBlocked -> StatusCard(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.block_reason_status_not_blocked),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            reason.blockedBy.isEmpty() -> StatusCard(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.block_reason_status_blocked_unknown),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
            else -> StatusCard(
                icon = Icons.Rounded.Block,
                text = pluralStringResource(
                    R.plurals.block_reason_status_blocked,
                    reason.blockedBy.size,
                    reason.blockedBy.size,
                ),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        if (reason.blockedBy.isNotEmpty()) {
            MatchSection(
                title = stringResource(R.string.block_reason_section_blocked_by),
                matches = reason.blockedBy,
                accent = MaterialTheme.colorScheme.error,
            )
        }
        if (reason.allowedBy.isNotEmpty()) {
            MatchSection(
                title = stringResource(R.string.block_reason_section_allowed_by),
                matches = reason.allowedBy,
                accent = MaterialTheme.colorScheme.primary,
            )
        }
        if (reason.isCurrentlyBlocked && reason.blockedBy.any { it.source == BlockRuleSource.CUSTOM_DOMAIN }) {
            NoteText(stringResource(R.string.block_reason_note_custom_wins))
        }
        if (reason.unreadableLists.isNotEmpty()) {
            NoteText(
                stringResource(
                    R.string.block_reason_note_unreadable,
                    reason.unreadableLists.joinToString(", "),
                )
            )
        }
    }
}

@Composable
private fun StatusCard(icon: ImageVector, text: String, container: Color, content: Color) {
    Surface(
        shape = BbdnsShapes.card,
        color = container,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun MatchSection(title: String, matches: List<BlockMatch>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier.semantics { heading() },
        )
        matches.forEach { MatchCard(it) }
    }
}

@Composable
private fun MatchCard(match: BlockMatch) {
    Surface(
        shape = BbdnsShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = match.sourceName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            SelectionContainer {
                Text(
                    text = match.rule,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(stringResource(match.source.labelRes()))
                Pill(stringResource(match.type.labelRes()))
            }
        }
    }
}

@Composable
private fun Pill(text: String) {
    Surface(
        shape = BbdnsShapes.chip,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun NoteText(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun BlockMatchType.labelRes(): Int = when (this) {
    BlockMatchType.EXACT -> R.string.block_reason_type_exact
    BlockMatchType.PARENT_DOMAIN -> R.string.block_reason_type_parent
    BlockMatchType.TLD -> R.string.block_reason_type_tld
    BlockMatchType.WILDCARD -> R.string.block_reason_type_wildcard
    BlockMatchType.REGEX -> R.string.block_reason_type_regex
}

private fun BlockRuleSource.labelRes(): Int = when (this) {
    BlockRuleSource.LIST -> R.string.block_reason_source_list
    BlockRuleSource.USER_RULE -> R.string.block_reason_source_user_rule
    BlockRuleSource.CUSTOM_DOMAIN -> R.string.block_reason_source_custom
}

private fun copyDomain(context: Context, domain: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(domain, domain))
    Toast.makeText(context, context.getString(R.string.block_reason_copied), Toast.LENGTH_SHORT).show()
}
