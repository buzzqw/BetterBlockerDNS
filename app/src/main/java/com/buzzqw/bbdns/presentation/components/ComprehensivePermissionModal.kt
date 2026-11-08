/*
 * Copyright (C) 2025-2026 Vexzure
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

package com.buzzqw.bbdns.presentation.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.buzzqw.bbdns.R
import com.buzzqw.bbdns.core.utils.extensions.requestDisableBatteryOptimization
import kotlinx.coroutines.launch

/**
 * BBDNS is VPN-only: the onboarding asks for the Android VPN permission and the
 * two runtime permissions needed for a reliable background service.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComprehensivePermissionModal(
    context: Context,
    sheetState: SheetState,
    onPermissionsComplete: (useVpn: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(PermissionStep.VPN_METHOD) }

    val scope = rememberCoroutineScope()

    fun finish() {
        context.requestDisableBatteryOptimization()
        onPermissionsComplete(true)
        scope.launch { sheetState.hide() }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Proceed regardless of notification permission; the app still works.
        finish()
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                finish()
            }
        } else {
            onDismiss()
        }
    }

    fun afterVpnGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finish()
        }
    }

    if (sheetState.isVisible) {
        ModalBottomSheet(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            RoundedCornerShape(2.dp)
                        )
                )
            },
            content = {
                when (currentStep) {
                    PermissionStep.VPN_METHOD -> VpnMethodSelection(
                        onVpnSelected = {
                            currentStep = PermissionStep.VPN_PERMISSION
                        }
                    )
                    PermissionStep.VPN_PERMISSION -> VpnPermissionRequest(
                        context = context,
                        vpnLauncher = vpnLauncher,
                        onAlreadyGranted = { afterVpnGranted() }
                    )
                }
            }
        )
    }
}

@Composable
private fun VpnMethodSelection(onVpnSelected: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 32.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.vpn_permission_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.vpn_permission_description),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onVpnSelected,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.method_use_vpn),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.permissions_vpn_explanation),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun VpnPermissionRequest(
    context: Context,
    vpnLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>,
    onAlreadyGranted: () -> Unit
) {
    LaunchedEffect(Unit) {
        val intent = android.net.VpnService.prepare(context)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            onAlreadyGranted()
        }
    }

    Column(
        modifier = Modifier
            .padding(24.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.permissions_setup),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.permissions_grant_required),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private enum class PermissionStep {
    VPN_METHOD,
    VPN_PERMISSION
}
