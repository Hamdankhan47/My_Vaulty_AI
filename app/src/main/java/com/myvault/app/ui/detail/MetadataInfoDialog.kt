package com.myvault.app.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myvault.app.domain.model.Document
import com.myvault.app.domain.model.ExtractionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MetadataInfoDialog(
    document: Document,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = "Document Metadata",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                MetadataRow(label = "Display Name", value = document.displayName.ifBlank { document.title })
                MetadataRow(label = "Original File Name", value = document.originalFileName.ifBlank { document.title })
                MetadataRow(label = "Type / MIME", value = "${document.fileType} (${document.mimeType})")
                MetadataRow(label = "Size", value = formatFileSize(document.fileSize))
                MetadataRow(label = "Created", value = formatFullDate(document.createdAt))
                MetadataRow(label = "Updated", value = formatFullDate(document.updatedAt))
                MetadataRow(label = "Status", value = formatExtractionStatus(document.extractionStatus))

                if (!document.extractionError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Extraction Detail",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = document.extractionError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatFullDate(timestamp: Long): String {
    if (timestamp <= 0L) return "N/A"
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatFileSize(sizeInBytes: Long): String {
    if (sizeInBytes <= 0) return "0 B"
    val kb = sizeInBytes / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.2f MB", mb)
    } else {
        String.format(Locale.US, "%.1f KB", kb)
    }
}

private fun formatExtractionStatus(status: ExtractionStatus): String {
    return when (status) {
        ExtractionStatus.NOT_PROCESSED -> "Not Processed"
        ExtractionStatus.OCR_COMPLETED -> "OCR Complete — AI Pending"
        ExtractionStatus.AI_PROCESSING -> "Processing with AI..."
        ExtractionStatus.AI_COMPLETED -> "AI Extraction Complete"
        ExtractionStatus.AI_FAILED -> "AI Extraction Failed"
    }
}
