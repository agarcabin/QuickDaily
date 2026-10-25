package com.quickdaily.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quickdaily.R
import com.quickdaily.VaultUpgradeResult

@Composable
internal fun VaultUpgradeDialog(
    state: VaultUpgradeResult,
    onGrantAccess: () -> Unit,
    onReselect: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is VaultUpgradeResult.Ready) return
    val needsPermission = state is VaultUpgradeResult.PermissionRequired
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.qd_vault_upgrade_title)) },
        text = {
            Text(stringResource(if (needsPermission) R.string.qd_vault_upgrade_permission else R.string.qd_vault_upgrade_reselect),
                modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
        },
        confirmButton = {
            TextButton(onClick = if (needsPermission) onGrantAccess else onReselect,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (needsPermission) R.string.qd_vault_upgrade_grant else R.string.qd_vault_upgrade_choose))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.qd_vault_upgrade_later))
            }
        },
    )
}
