package com.myvault.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun EditDisplayNameDialog(
    currentDisplayName: String,
    onDismissRequest: () -> Unit,
    onSaveDisplayName: (String) -> Unit
) {
    var editedName by remember { mutableStateOf(currentDisplayName) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Edit Display Name") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Change user-facing name for this document. Original file name on disk remains unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = editedName.trim()
                    if (trimmed.isNotBlank()) {
                        onSaveDisplayName(trimmed)
                        onDismissRequest()
                    }
                },
                enabled = editedName.trim().isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
