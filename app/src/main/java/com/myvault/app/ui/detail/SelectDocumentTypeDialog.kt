package com.myvault.app.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myvault.app.domain.model.PredefinedDocumentTypes

@Composable
fun SelectDocumentTypeDialog(
    currentDocumentType: String,
    onDismissRequest: () -> Unit,
    onSelectDocumentType: (type: String, category: String) -> Unit
) {
    var isCustomSelected by remember { mutableStateOf(currentDocumentType !in PredefinedDocumentTypes.ALL_PREDEFINED_TYPES && currentDocumentType.isNotBlank() && currentDocumentType != "UNKNOWN" && currentDocumentType != "Custom") }
    var selectedPredefinedType by remember { mutableStateOf(if (isCustomSelected) "" else currentDocumentType) }
    var customTypeName by remember { mutableStateOf(if (isCustomSelected) currentDocumentType else "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = "Select Document Type") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Predefined Types grouped by Category
                for ((category, types) in PredefinedDocumentTypes.PREDEFINED_TYPES_BY_CATEGORY) {
                    Text(
                        text = category.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    for (type in types) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isCustomSelected = false
                                    selectedPredefinedType = type
                                    errorMessage = null
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !isCustomSelected && selectedPredefinedType.equals(type, ignoreCase = true),
                                onClick = {
                                    isCustomSelected = false
                                    selectedPredefinedType = type
                                    errorMessage = null
                                }
                            )
                            Text(
                                text = type,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                // Custom Option
                Text(
                    text = "CUSTOM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustomSelected = true }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = isCustomSelected,
                        onClick = { isCustomSelected = true }
                    )
                    Text(
                        text = "Custom Document Type",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (isCustomSelected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customTypeName,
                        onValueChange = {
                            customTypeName = it
                            if (errorMessage != null && it.trim().isNotBlank()) {
                                errorMessage = null
                            }
                        },
                        label = { Text("Custom Type Name") },
                        isError = errorMessage != null,
                        supportingText = if (errorMessage != null) {
                            { Text(errorMessage!!) }
                        } else null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isCustomSelected) {
                        val trimmed = customTypeName.trim()
                        if (trimmed.isBlank()) {
                            errorMessage = "Custom type name cannot be empty"
                        } else if (trimmed.equals("Custom", ignoreCase = true)) {
                            errorMessage = "Please enter a specific custom type name"
                        } else {
                            val matched = PredefinedDocumentTypes.normalizeAndMatch(trimmed)
                            onSelectDocumentType(matched.docType, matched.category)
                            onDismissRequest()
                        }
                    } else if (selectedPredefinedType.isNotBlank()) {
                        val category = PredefinedDocumentTypes.getCategoryForType(selectedPredefinedType)
                        onSelectDocumentType(selectedPredefinedType, category)
                        onDismissRequest()
                    }
                }
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
