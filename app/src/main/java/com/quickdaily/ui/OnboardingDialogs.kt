package com.quickdaily.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.quickdaily.R
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn

@Composable
internal fun OnboardingSkipConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 400.dp),
        title = { Text(stringResource(R.string.qd_onboarding_skip_title)) },
        text = { Text(stringResource(R.string.qd_onboarding_skip_message)) },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.qd_common_cancel)) }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.qd_onboarding_skip)) }
        },
    )
}
