package com.myvault.app.ui.home

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.myvault.app.domain.model.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private object ThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(40)

    fun get(key: String): ImageBitmap? = cache.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

private data class PendingImport(
    val uri: Uri,
    val fileType: String,
    val customCategory: String,
    val initialSuggestedName: String
)

private val GridViewIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "GridViewIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 3f)
        horizontalLineTo(11f)
        verticalLineTo(11f)
        horizontalLineTo(3f)
        close()

        moveTo(13f, 3f)
        horizontalLineTo(21f)
        verticalLineTo(11f)
        horizontalLineTo(13f)
        close()

        moveTo(3f, 13f)
        horizontalLineTo(11f)
        verticalLineTo(21f)
        horizontalLineTo(3f)
        close()

        moveTo(13f, 13f)
        horizontalLineTo(21f)
        verticalLineTo(21f)
        horizontalLineTo(13f)
        close()
    }.build()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onDocumentClick: (Document) -> Unit,
    onImportSuccess: (Document) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddOptionsSheet by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var documentToDelete by remember { mutableStateOf<Document?>(null) }
    val sheetState = rememberModalBottomSheetState()

    fun handlePendingUri(uri: Uri, fileType: String, customCategory: String) {
        val originalName = getFileNameFromUri(context, uri)
        val suggestedName = if (originalName.contains(".")) {
            originalName.substringBeforeLast(".")
        } else {
            originalName
        }
        pendingImport = PendingImport(
            uri = uri,
            fileType = fileType,
            customCategory = customCategory,
            initialSuggestedName = suggestedName
        )
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pdfUri = scanningResult?.pdf?.uri
            if (pdfUri != null) {
                handlePendingUri(pdfUri, fileType = "PDF", customCategory = "Scanned")
            } else {
                val pages = scanningResult?.pages
                val imageUri = pages?.firstOrNull()?.imageUri
                if (imageUri != null) {
                    handlePendingUri(imageUri, fileType = "IMAGE", customCategory = "Scanned")
                }
            }
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handlePendingUri(uri, fileType = "IMAGE", customCategory = "Image")
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handlePendingUri(uri, fileType = "PDF", customCategory = "Document")
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MyVault",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.setGridView(!isGridView) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isGridView) Icons.AutoMirrored.Filled.List else GridViewIcon,
                            contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "MyVault",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Your documents, organized.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { showAddOptionsSheet = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isImporting
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Document"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Add Document")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Recent Documents",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.recentDocuments.isEmpty() && !uiState.isLoading && !uiState.isImporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No documents yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scan or upload a bill or document to get started.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                } else if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.recentDocuments, key = { it.id }) { document ->
                            DocumentGridItem(
                                document = document,
                                onClick = { onDocumentClick(document) },
                                onDeleteClick = { documentToDelete = document }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.recentDocuments, key = { it.id }) { document ->
                            DocumentListItem(
                                document = document,
                                onClick = { onDocumentClick(document) },
                                onDeleteClick = { documentToDelete = document }
                            )
                        }
                    }
                }
            }

            if (uiState.isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Saving document...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text(text = "Delete document?") },
            text = {
                Text(text = "Are you sure you want to delete '${doc.displayName.ifBlank { doc.title }}'? This will permanently remove the document and its file.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = documentToDelete
                        documentToDelete = null
                        if (target != null) {
                            viewModel.deleteDocument(
                                document = target,
                                onSuccess = {},
                                onError = { errorMsg ->
                                    Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
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
                TextButton(onClick = { documentToDelete = null }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    pendingImport?.let { pending ->
        NameDocumentDialog(
            initialName = pending.initialSuggestedName,
            onDismissRequest = { pendingImport = null },
            onConfirm = { customDisplayName ->
                val importItem = pendingImport
                pendingImport = null
                if (importItem != null) {
                    viewModel.importDocument(
                        uri = importItem.uri,
                        fileType = importItem.fileType,
                        customCategory = importItem.customCategory,
                        customDisplayName = customDisplayName,
                        onSuccess = { newDocument ->
                            onImportSuccess(newDocument)
                        }
                    )
                }
            }
        )
    }

    if (showAddOptionsSheet) {
        AddDocumentOptionSheet(
            sheetState = sheetState,
            onDismissRequest = { showAddOptionsSheet = false },
            onScanDocumentClick = {
                val options = GmsDocumentScannerOptions.Builder()
                    .setGalleryImportAllowed(false)
                    .setPageLimit(10)
                    .setResultFormats(
                        GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                        GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                    )
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                    .build()

                val scanner = GmsDocumentScanning.getClient(options)
                val activity = context as? Activity
                if (activity != null) {
                    scanner.getStartScanIntent(activity)
                        .addOnSuccessListener { intentSender ->
                            scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Scan error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                }
            },
            onChooseImageClick = {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onChoosePdfClick = {
                pdfPickerLauncher.launch(arrayOf("application/pdf"))
            }
        )
    }
}

@Composable
fun DocumentListItem(
    document: Document,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val file = File(document.filePath)
    val pdfThumbnail = rememberPdfThumbnail(file = file, fileType = document.fileType, targetSize = 256)
    val imageThumbnail = rememberImageThumbnail(file = file, fileType = document.fileType, targetSize = 256)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Small Thumbnail Box (56dp)
            Box(
                modifier = Modifier
                    .size(56.dp)
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
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.displayName.ifBlank { document.title },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${document.category}  •  ${document.documentType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Document",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun DocumentGridItem(
    document: Document,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val file = File(document.filePath)
    val pdfThumbnail = rememberPdfThumbnail(file = file, fileType = document.fileType, targetSize = 512)
    val imageThumbnail = rememberImageThumbnail(file = file, fileType = document.fileType, targetSize = 512)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            // Larger Grid Thumbnail Box (130dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
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
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = document.displayName.ifBlank { document.title },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${document.fileType}  •  ${document.documentType}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Document",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberPdfThumbnail(file: File, fileType: String, targetSize: Int = 384): ImageBitmap? {
    val cacheKey = "pdf_${file.absolutePath}_$targetSize"
    var bitmapState by remember(cacheKey) { mutableStateOf(ThumbnailCache.get(cacheKey)) }

    if (bitmapState == null && fileType.equals("PDF", ignoreCase = true) && file.exists() && file.length() > 0L) {
        LaunchedEffect(cacheKey) {
            withContext(Dispatchers.IO) {
                try {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        val scale = targetSize.toFloat() / Math.max(page.width, page.height)
                        val renderWidth = Math.max(1, (page.width * scale).toInt())
                        val renderHeight = Math.max(1, (page.height * scale).toInt())
                        val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()
                        pfd.close()

                        val imageBitmap = bitmap.asImageBitmap()
                        ThumbnailCache.put(cacheKey, imageBitmap)
                        withContext(Dispatchers.Main) {
                            bitmapState = imageBitmap
                        }
                    } else {
                        renderer.close()
                        pfd.close()
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
    return bitmapState
}

@Composable
private fun rememberImageThumbnail(file: File, fileType: String, targetSize: Int = 384): ImageBitmap? {
    val cacheKey = "img_${file.absolutePath}_$targetSize"
    var bitmapState by remember(cacheKey) { mutableStateOf(ThumbnailCache.get(cacheKey)) }

    if (bitmapState == null && !fileType.equals("PDF", ignoreCase = true) && file.exists() && file.length() > 0L) {
        LaunchedEffect(cacheKey) {
            withContext(Dispatchers.IO) {
                try {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(file.absolutePath, options)
                    var sampleSize = 1
                    while (options.outWidth / sampleSize > targetSize || options.outHeight / sampleSize > targetSize) {
                        sampleSize *= 2
                    }
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
                    if (bitmap != null) {
                        val imageBitmap = bitmap.asImageBitmap()
                        ThumbnailCache.put(cacheKey, imageBitmap)
                        withContext(Dispatchers.Main) {
                            bitmapState = imageBitmap
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
    return bitmapState
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = ""
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = cursor.getString(index)
                }
            }
        }
    }
    if (name.isBlank()) {
        name = uri.lastPathSegment ?: "Document_${System.currentTimeMillis()}"
    }
    return name
}
