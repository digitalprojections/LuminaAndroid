package com.oneimage.android.ui.shared

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun CancelTaskConfirmationDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cancel this task?") },
        text = { Text("Any in-progress work for this task will be stopped.") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Cancel task")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep running")
            }
        }
    )
}
