package com.myvault.app.ui.detail

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.myvault.app.domain.model.Document
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.ExtractionStatus
import com.myvault.app.domain.model.FieldType
import com.myvault.app.domain.model.OcrStatus
import com.myvault.app.domain.model.PredefinedDocumentTypes
import com.myvault.app.ui.home.HomeViewModel
import java.io.File

data class DocumentActionsModel(
    val phone: String?,
    val email: String?,
    val address: String?,
    val url: String?,
    val contactName: String?,
    val organization: String?
) {
    val canSaveContact: Boolean
        get() = !contactName.isNullOrBlank() || !organization.isNullOrBlank() || !phone.isNullOrBlank() || !email.isNullOrBlank()

    val hasAnyAction: Boolean
        get() = canSaveContact || !phone.isNullOrBlank() || !email.isNullOrBlank() || !address.isNullOrBlank() || !url.isNullOrBlank()
}

fun extractDocumentActions(fields: List<DocumentField>): DocumentActionsModel {
    val personName = fields.firstOrNull { it.fieldType == FieldType.PERSON_NAME }?.fieldValue
        ?: fields.firstOrNull { f ->
            val l = f.fieldName.lowercase().trim()
            listOf("contact person", "person name", "customer name", "customer", "person", "owner").any { l.contains(it) } && f.fieldValue.isNotBlank()
        }?.fieldValue

    val organization = fields.firstOrNull { it.fieldType == FieldType.ORGANIZATION }?.fieldValue
        ?: fields.firstOrNull { f ->
            val l = f.fieldName.lowercase().trim()
            listOf("company", "organization", "provider").any { l.contains(it) } && f.fieldValue.isNotBlank()
        }?.fieldValue

    val phone = fields.firstOrNull { it.fieldType == FieldType.PHONE }?.fieldValue
        ?: fields.firstOrNull { f ->
            val l = f.fieldName.lowercase().trim()
            listOf("phone", "mobile", "contact", "tel").any { l.contains(it) } && f.fieldValue.isNotBlank()
        }?.fieldValue

    val email = fields.firstOrNull { it.fieldType == FieldType.EMAIL }?.fieldValue
        ?: fields.firstOrNull { f ->
            val l = f.fieldName.lowercase().trim()
            l.contains("email") && f.fieldValue.isNotBlank()
        }?.fieldValue

    val address = fields.firstOrNull { it.fieldType == FieldType.ADDRESS }?.fieldValue
        ?: fields.firstOrNull { f ->
            val l = f.fieldName.lowercase().trim()
            listOf("address", "location").any { l.contains(it) } && f.fieldValue.isNotBlank()
        }?.fieldValue

    val url = fields.firstOrNull { it.fieldType == FieldType.URL }?.fieldValue
        ?: fields.firstOrNull { f ->
            val l = f.fieldName.lowercase().trim()
            listOf("website", "site", "url", "link").any { l.contains(it) } && f.fieldValue.isNotBlank()
        }?.fieldValue

    return DocumentActionsModel(
        phone = phone?.trim()?.ifBlank { null },
        email = email?.trim()?.ifBlank { null },
        address = address?.trim()?.ifBlank { null },
        url = url?.trim()?.ifBlank { null },
        contactName = personName?.trim()?.ifBlank { null },
        organization = organization?.trim()?.ifBlank { null }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    initialDocument: Document,
    viewModel: HomeViewModel,
    onBackClick: () -> Unit,
    onEditFieldsClick: (Document) -> Unit,
    onOpenFileClick: (Document) -> Unit = {}
) {
    val context = LocalContext.current

    // Stable reactive Flow subscription to Room database using initialDocument.id
    val documentFlow = remember(initialDocument.id) { viewModel.getDocumentFlow(initialDocument.id) }
    val liveDocumentState by documentFlow.collectAsState(initial = initialDocument)
    val document = liveDocumentState ?: initialDocument

    val fieldsFlow = remember(initialDocument.id) { viewModel.getDocumentFieldsFlow(initialDocument.id) }
    val fields by fieldsFlow.collectAsState(initial = emptyList())

    val file = File(document.filePath)

    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showSelectDocTypeDialog by remember { mutableStateOf(false) }

    val pdfThumbnail = rememberPdfThumbnail(file = file, fileType = document.fileType)
    val imageThumbnail = rememberImageThumbnail(file = file, fileType = document.fileType)
    val documentActions = remember(fields) { extractDocumentActions(fields) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Document Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to previous screen"
                        )
                    }
                },
                actions = {
                    // Blue Info Icon Button
                    IconButton(onClick = { showMetadataDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Document Metadata Info",
                            tint = Color(0xFF1976D2)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Document Main Header Card with Thumbnail Preview & Compact Meta
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    // Prominent Enlarged Thumbnail Container (260.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            imageThumbnail != null -> {
                                Image(
                                    bitmap = imageThumbnail,
                                    contentDescription = "Document Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            pdfThumbnail != null -> {
                                Image(
                                    bitmap = pdfThumbnail,
                                    contentDescription = "PDF Page Thumbnail",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            else -> {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Document File Icon",
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Title Row with Edit Display Name Action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = document.displayName.ifBlank { document.title },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (document.originalFileName.isNotBlank() && document.originalFileName != document.displayName) {
                                Text(
                                    text = "Original: ${document.originalFileName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = { showEditNameDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Display Name",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Tight Compact Metadata Line with Edit Document Type
                    val isCustomDocType = document.documentType !in PredefinedDocumentTypes.ALL_PREDEFINED_TYPES &&
                            document.documentType != "UNKNOWN" &&
                            document.documentType != "Custom"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${document.category}  •  ${document.fileType}  •  ${document.documentType}${if (isCustomDocType) " (Custom)" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { showSelectDocTypeDialog = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text("Change Type", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button: Open File
            Button(
                onClick = { onOpenFileClick(document) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Open / View Original File")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reprocess OCR & Always-Visible Reprocess/Retry AI Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var isOcrProcessing by remember { mutableStateOf(false) }

                OutlinedButton(
                    onClick = {
                        isOcrProcessing = true
                        viewModel.reprocessOcr(document) { result ->
                            isOcrProcessing = false
                            if (result.isFailure) {
                                Toast.makeText(context, result.exceptionOrNull()?.localizedMessage ?: "OCR Reprocess failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isOcrProcessing && document.ocrStatus != OcrStatus.PROCESSING,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isOcrProcessing || document.ocrStatus == OcrStatus.PROCESSING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "OCR...", style = MaterialTheme.typography.labelMedium)
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reprocess OCR Action",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Reprocess OCR", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    AiActionButton(
                        extractionStatus = document.extractionStatus,
                        ocrText = document.ocrText,
                        onProcessClick = {
                            viewModel.retryAiExtraction(document.id)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Structured Fields Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Structured Fields",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(onClick = { onEditFieldsClick(document) }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit or Review Fields"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Edit / Review")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (fields.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Text(
                        text = "No structured fields available. Tap 'Edit / Review' to add fields.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        fields.forEachIndexed { index, field ->
                            StructuredFieldItem(field = field)
                            if (index < fields.size - 1) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            // Single Consolidated Document Actions Section
            if (documentActions.hasAnyAction) {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Document Actions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Save Contact
                        if (documentActions.canSaveContact) {
                            AssistChip(
                                onClick = { launchSaveContactAction(context, documentActions) },
                                label = { Text("Save Contact") }
                            )
                        }
                        // 2. Call
                        if (!documentActions.phone.isNullOrBlank()) {
                            AssistChip(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${documentActions.phone}"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Unable to open phone application.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("Call") },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Call Number", modifier = Modifier.size(16.dp)) }
                            )
                        }
                        // 3. Email
                        if (!documentActions.email.isNullOrBlank()) {
                            AssistChip(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${documentActions.email}"))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No email application is available.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("Email") },
                                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Send Email", modifier = Modifier.size(16.dp)) }
                            )
                        }
                        // 4. Address / Open in Maps
                        if (!documentActions.address.isNullOrBlank()) {
                            AssistChip(
                                onClick = {
                                    val mapUri = Uri.parse("geo:0,0?q=${Uri.encode(documentActions.address)}")
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, mapUri)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "No maps application is available.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("Address") }
                            )
                        }
                        // 5. Open Link
                        if (!documentActions.url.isNullOrBlank()) {
                            AssistChip(
                                onClick = {
                                    val rawUrl = documentActions.url
                                    val normalizedUrl = when {
                                        rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true) -> rawUrl
                                        rawUrl.startsWith("www.", ignoreCase = true) -> "https://$rawUrl"
                                        else -> "https://$rawUrl"
                                    }
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(normalizedUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Invalid link or no browser available.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("Open Link") }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // OCR Section Header & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "OCR Text",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OcrStatusBadge(status = document.ocrStatus)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // OCR Failure / Retry Bar
            if (document.ocrStatus == OcrStatus.FAILED || document.ocrStatus == OcrStatus.PENDING) {
                OutlinedButton(
                    onClick = { viewModel.retryOcr(document) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry OCR Processing"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (document.ocrStatus == OcrStatus.FAILED) "Retry OCR" else "Start OCR")
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Raw OCR Text Container with Dynamic Adaptive Sizing & Internal Scroll
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    when {
                        document.ocrStatus == OcrStatus.PROCESSING -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 16.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Processing local ML Kit OCR...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        !document.ocrText.isNullOrBlank() -> {
                            val textContent = document.ocrText
                            val ocrScrollState = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 248.dp)
                                    .verticalScroll(ocrScrollState)
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = textContent,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        document.ocrStatus == OcrStatus.FAILED -> {
                            Text(
                                text = "OCR extraction failed. You can tap 'Retry OCR' above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {
                            Text(
                                text = "Text extraction pending...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Delete Document Button
            OutlinedButton(
                onClick = { showDeleteConfirmationDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Document",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    if (showSelectDocTypeDialog) {
        SelectDocumentTypeDialog(
            currentDocumentType = document.documentType,
            onDismissRequest = { showSelectDocTypeDialog = false },
            onSelectDocumentType = { selectedType, category ->
                viewModel.updateDocumentType(document.id, selectedType, category, source = "USER")
            }
        )
    }

    if (showMetadataDialog) {
        MetadataInfoDialog(
            document = document,
            onDismissRequest = { showMetadataDialog = false }
        )
    }

    if (showEditNameDialog) {
        EditDisplayNameDialog(
            currentDisplayName = document.displayName.ifBlank { document.title },
            onDismissRequest = { showEditNameDialog = false },
            onSaveDisplayName = { newName ->
                viewModel.updateDisplayName(document.id, newName)
            }
        )
    }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text(text = "Delete document?") },
            text = {
                Text(text = "This will permanently delete the document and its stored file. This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(
                            document = document,
                            onSuccess = {
                                showDeleteConfirmationDialog = false
                                onBackClick()
                            },
                            onError = { errorMsg ->
                                showDeleteConfirmationDialog = false
                                Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmationDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}

@Composable
private fun StructuredFieldItem(field: DocumentField) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = field.fieldName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = field.fieldValue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${field.fieldType.name} • ${field.source.name}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

private fun launchSaveContactAction(
    context: Context,
    actions: DocumentActionsModel
) {
    val contactName = actions.contactName ?: actions.organization
    val company = actions.organization
    val phone = actions.phone
    val email = actions.email

    try {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            if (!contactName.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.NAME, contactName.trim())
            }
            if (!company.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.COMPANY, company.trim())
            }
            if (!phone.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.PHONE, phone.trim())
            }
            if (!email.isNullOrBlank()) {
                putExtra(ContactsContract.Intents.Insert.EMAIL, email.trim())
            }
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open contact application.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AiActionButton(
    extractionStatus: ExtractionStatus,
    ocrText: String?,
    onProcessClick: () -> Unit
) {
    val context = LocalContext.current
    val (buttonText, isProcessing) = when (extractionStatus) {
        ExtractionStatus.NOT_PROCESSED, ExtractionStatus.OCR_COMPLETED -> "Process with AI" to false
        ExtractionStatus.AI_PROCESSING -> "Processing with AI..." to true
        ExtractionStatus.AI_COMPLETED -> "Reprocess with AI" to false
        ExtractionStatus.AI_FAILED -> "Retry AI" to false
    }

    Button(
        onClick = {
            if (ocrText.isNullOrBlank()) {
                Toast.makeText(context, "No OCR text available to process with AI", Toast.LENGTH_SHORT).show()
            } else {
                onProcessClick()
            }
        },
        enabled = !isProcessing,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "$buttonText Action"
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = buttonText, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun rememberPdfThumbnail(file: File, fileType: String): ImageBitmap? {
    return remember(file.absolutePath, fileType) {
        if (!fileType.equals("PDF", ignoreCase = true) || !file.exists() || file.length() == 0L) {
            return@remember null
        }
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                return@remember null
            }
            val page = renderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            renderer.close()
            pfd.close()
            bitmap.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
private fun rememberImageThumbnail(file: File, fileType: String): ImageBitmap? {
    return remember(file.absolutePath, fileType) {
        if (fileType.equals("PDF", ignoreCase = true) || !file.exists() || file.length() == 0L) {
            return@remember null
        }
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            var sampleSize = 1
            while (options.outWidth / sampleSize > 1024 || options.outHeight / sampleSize > 1024) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            bitmap?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
private fun OcrStatusBadge(status: OcrStatus) {
    val (bgColor, textColor, text) = when (status) {
        OcrStatus.COMPLETED -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "OCR: Completed"
        )
        OcrStatus.PROCESSING -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "OCR: Processing..."
        )
        OcrStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "OCR: Failed"
        )
        OcrStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "OCR: Pending"
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
