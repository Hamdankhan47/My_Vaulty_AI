package com.myvault.app.ui.home

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.myvault.app.data.local.LocalFileStorageManager
import com.myvault.app.data.remote.RetrofitClient
import com.myvault.app.domain.model.Document
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.ExtractionStatus
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.OcrStatus
import com.myvault.app.domain.model.PredefinedDocumentTypes
import com.myvault.app.domain.repository.DocumentRepository
import com.myvault.app.extraction.AddressConsolidator
import com.myvault.app.extraction.AiFieldExtractor
import com.myvault.app.extraction.FieldExtractor
import com.myvault.app.extraction.LocalFieldExtractor
import com.myvault.app.ocr.MlKitOcrEngine
import com.myvault.app.ocr.OcrEngine
import com.myvault.app.ocr.OcrResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HomeViewModel(
    private val documentRepository: DocumentRepository,
    private val fileStorageManager: LocalFileStorageManager,
    private val ocrEngine: OcrEngine,
    private val aiFieldExtractor: FieldExtractor,
    private val localFieldExtractor: FieldExtractor,
    context: Context? = null
) : ViewModel() {

    private val prefs = try {
        context?.getSharedPreferences("myvault_prefs", Context.MODE_PRIVATE)
    } catch (_: Exception) {
        null
    }
    private val _isGridView = MutableStateFlow(prefs?.getBoolean("is_grid_view", false) ?: false)
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    fun getAllDocumentsFlow(): Flow<List<Document>> {
        return documentRepository.getAllDocuments()
    }

    val uiState: StateFlow<HomeUiState> = combine(
        documentRepository.getRecentDocuments(),
        _isImporting,
        _errorMessage
    ) { documents, importing, error ->
        HomeUiState(
            recentDocuments = documents,
            isLoading = false,
            isImporting = importing,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun setGridView(isGrid: Boolean) {
        _isGridView.value = isGrid
        prefs?.edit()?.putBoolean("is_grid_view", isGrid)?.apply()
    }

    fun getDocumentFlow(id: Long): Flow<Document?> {
        return documentRepository.getDocumentByIdFlow(id)
    }

    fun getDocumentFieldsFlow(documentId: Long): Flow<List<DocumentField>> {
        return documentRepository.getDocumentFieldsFlow(documentId)
    }

    fun updateDisplayName(documentId: Long, newDisplayName: String) {
        val cleanName = newDisplayName.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch {
            documentRepository.updateDocumentDisplayName(documentId, cleanName)
        }
    }

    fun updateDocumentType(documentId: Long, newType: String, newCategory: String, source: String = "USER") {
        val cleanType = newType.trim()
        val cleanCategory = newCategory.trim()
        if (cleanType.isBlank()) return
        viewModelScope.launch {
            documentRepository.updateDocumentType(documentId, cleanType, cleanCategory, source)
        }
    }

    fun importDocument(
        uri: Uri,
        fileType: String,
        customCategory: String = "General",
        documentType: String = "UNKNOWN",
        customDisplayName: String? = null,
        onSuccess: (Document) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isImporting.value = true
            _errorMessage.value = null

            val saveResult = fileStorageManager.saveFileFromUri(uri, fileType)
            saveResult.fold(
                onSuccess = { savedFile ->
                    val finalDisplayName = customDisplayName?.trim()?.ifBlank { null } ?: savedFile.fileName
                    val document = Document(
                        title = finalDisplayName,
                        displayName = finalDisplayName,
                        originalFileName = savedFile.fileName,
                        category = customCategory,
                        documentType = documentType,
                        documentTypeSource = "AI",
                        dateAddedTimestamp = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        filePath = savedFile.filePath,
                        fileType = fileType.uppercase(),
                        mimeType = savedFile.mimeType,
                        fileSize = savedFile.fileSize,
                        ocrStatus = OcrStatus.PROCESSING,
                        extractionStatus = ExtractionStatus.NOT_PROCESSED
                    )
                    val documentId = documentRepository.insertDocument(document)
                    val savedDocument = document.copy(id = documentId)
                    _isImporting.value = false

                    onSuccess(savedDocument)

                    // Asynchronously run local ML Kit OCR & Field Extraction
                    runOcrForDocument(documentId, savedFile.filePath, fileType, documentType)
                },
                onFailure = { error ->
                    _isImporting.value = false
                    _errorMessage.value = error.localizedMessage ?: "Failed to save document file."
                }
            )
        }
    }

    fun retryOcr(document: Document) {
        val file = File(document.filePath)
        if (!file.exists()) {
            _errorMessage.value = "Original file missing. Cannot retry OCR."
            viewModelScope.launch {
                documentRepository.updateOcrStatus(document.id, OcrStatus.FAILED)
                documentRepository.updateExtractionStatus(document.id, ExtractionStatus.AI_FAILED, "Original file missing")
            }
            return
        }
        runOcrForDocument(document.id, document.filePath, document.fileType, document.documentType)
    }

    fun reprocessOcr(document: Document, onResult: (Result<Document>) -> Unit = {}) {
        val file = File(document.filePath)
        if (!file.exists()) {
            _errorMessage.value = "Original file missing. Cannot reprocess OCR."
            onResult(Result.failure(IllegalStateException("Original file missing")))
            return
        }

        viewModelScope.launch {
            documentRepository.updateOcrStatus(document.id, OcrStatus.PROCESSING)
            when (val result = ocrEngine.recognizeText(file, document.fileType)) {
                is OcrResult.Success -> {
                    Log.i("HomeViewModel", "OCR REPROCESS SUCCESS, documentId=${document.id}, textLength=${result.text.length}")
                    documentRepository.updateOcrResult(document.id, OcrStatus.COMPLETED, result.text)
                    documentRepository.updateExtractionStatus(document.id, ExtractionStatus.OCR_COMPLETED)

                    // Preserve USER fields while clearing stale AI/OCR fields
                    val existingFields = documentRepository.getDocumentFields(document.id)
                    val userFields = existingFields.filter { it.source == FieldSource.USER }
                    documentRepository.saveDocumentFields(document.id, userFields)

                    val updatedDoc = documentRepository.getDocumentById(document.id) ?: document
                    onResult(Result.success(updatedDoc))
                }
                is OcrResult.Failure -> {
                    Log.w("HomeViewModel", "OCR REPROCESS FAILED, error=${result.errorMessage}")
                    if (!document.ocrText.isNullOrBlank()) {
                        documentRepository.updateOcrStatus(document.id, OcrStatus.COMPLETED)
                    } else {
                        documentRepository.updateOcrResult(document.id, OcrStatus.FAILED, null)
                        documentRepository.updateExtractionStatus(document.id, ExtractionStatus.AI_FAILED, result.errorMessage)
                    }
                    _errorMessage.value = "OCR Reprocess failed: ${result.errorMessage}"
                    onResult(Result.failure(IllegalStateException(result.errorMessage)))
                }
            }
        }
    }

    fun retryAiExtraction(documentId: Long, onResult: (Result<Document>) -> Unit = {}) {
        viewModelScope.launch {
            val result = executeAiExtractionRetry(documentId)
            onResult(result)
        }
    }

    suspend fun executeAiExtractionRetry(documentId: Long): Result<Document> {
        val existingDocument = documentRepository.getDocumentById(documentId)
            ?: return Result.failure(IllegalStateException("Document not found"))

        val storedOcrText = existingDocument.ocrText
        if (storedOcrText.isNullOrBlank()) {
            documentRepository.updateExtractionStatus(documentId, ExtractionStatus.AI_FAILED, "No stored OCR text available for retry")
            return Result.failure(IllegalStateException("No stored OCR text available for retry"))
        }

        // Set status to AI_PROCESSING without re-running ML Kit OCR
        documentRepository.updateExtractionStatus(documentId, ExtractionStatus.AI_PROCESSING)

        return try {
            val aiResult = aiFieldExtractor.extract(existingDocument.documentType, storedOcrText)
            val existingFields = documentRepository.getDocumentFields(documentId)
            val mergedFields = mergeFieldsPreservingUser(existingFields, aiResult.fields)

            val matchedResult = PredefinedDocumentTypes.normalizeAndMatch(aiResult.inferredDocumentType)
            val shouldUpdateDocType = existingDocument.documentTypeSource != "USER"

            val updatedDoc = existingDocument.copy(
                documentType = if (shouldUpdateDocType && matchedResult.docType != "UNKNOWN") matchedResult.docType else existingDocument.documentType,
                category = if (shouldUpdateDocType && matchedResult.category.isNotBlank()) matchedResult.category else existingDocument.category,
                documentTypeSource = if (shouldUpdateDocType) "AI" else "USER",
                extractionStatus = ExtractionStatus.AI_COMPLETED,
                extractionError = null,
                updatedAt = System.currentTimeMillis()
            )

            documentRepository.updateDocumentWithFields(updatedDoc, mergedFields)
            Result.success(updatedDoc)
        } catch (e: Exception) {
            Log.w("HomeViewModel", "AI EXTRACTION RETRY FAILED: ${e.message}", e)
            val isOffline = isConnectivityError(e)
            val finalStatus = if (isOffline) ExtractionStatus.OCR_COMPLETED else ExtractionStatus.AI_FAILED
            val errorMsg = if (isOffline) {
                "Unable to connect to AI server. Check that the backend is running and the device is connected to the network."
            } else {
                (e.localizedMessage ?: e.javaClass.simpleName)
            }

            documentRepository.updateExtractionStatus(documentId, finalStatus, errorMsg)
            _errorMessage.value = "AI processing failed: $errorMsg"

            Result.failure(e)
        }
    }

    private fun runOcrForDocument(documentId: Long, filePath: String, fileType: String, documentType: String) {
        viewModelScope.launch {
            documentRepository.updateOcrStatus(documentId, OcrStatus.PROCESSING)
            documentRepository.updateExtractionStatus(documentId, ExtractionStatus.NOT_PROCESSED)
            val file = File(filePath)
            if (!file.exists()) {
                documentRepository.updateOcrResult(documentId, OcrStatus.FAILED, null)
                documentRepository.updateExtractionStatus(documentId, ExtractionStatus.AI_FAILED, "File not found")
                return@launch
            }

            when (val result = ocrEngine.recognizeText(file, fileType)) {
                is OcrResult.Success -> {
                    Log.i("MyVaultE2E", "OCR SUCCESS, documentId=$documentId, textLength=${result.text.length}")
                    documentRepository.updateOcrResult(documentId, OcrStatus.COMPLETED, result.text)
                    documentRepository.updateExtractionStatus(documentId, ExtractionStatus.OCR_COMPLETED)
                    Log.i("MyVaultE2E", "CALLING extractFields")
                    extractFields(documentId, documentType, result.text)
                }
                is OcrResult.Failure -> {
                    documentRepository.updateOcrResult(documentId, OcrStatus.FAILED, null)
                    documentRepository.updateExtractionStatus(documentId, ExtractionStatus.AI_FAILED, result.errorMessage)
                }
            }
        }
    }

    private suspend fun extractFields(documentId: Long, documentType: String, ocrText: String) {
        Log.i("MyVaultE2E", "extractFields START, documentId=$documentId, ocrTextLength=${ocrText.length}")
        val existingDocument = documentRepository.getDocumentById(documentId)
        if (existingDocument == null) {
            Log.i("MyVaultE2E", "DOCUMENT NOT FOUND - SKIPPING EXTRACTION")
            return
        }
        val existingFields = documentRepository.getDocumentFields(documentId)

        // Mark AI_PROCESSING state
        documentRepository.updateExtractionStatus(documentId, ExtractionStatus.AI_PROCESSING)

        try {
            // Attempt Online AI Extraction via FastAPI + Gemini
            Log.i("MyVaultE2E", "CALLING AiFieldExtractor")
            val aiResult = aiFieldExtractor.extract(documentType, ocrText)
            val mergedFields = mergeFieldsPreservingUser(existingFields, aiResult.fields)

            val matchedResult = PredefinedDocumentTypes.normalizeAndMatch(aiResult.inferredDocumentType)
            val shouldUpdateDocType = existingDocument.documentTypeSource != "USER"

            val updatedDoc = existingDocument.copy(
                documentType = if (shouldUpdateDocType && matchedResult.docType != "UNKNOWN") matchedResult.docType else existingDocument.documentType,
                category = if (shouldUpdateDocType && matchedResult.category.isNotBlank()) matchedResult.category else existingDocument.category,
                documentTypeSource = if (shouldUpdateDocType) "AI" else "USER",
                extractionStatus = ExtractionStatus.AI_COMPLETED,
                extractionError = null,
                updatedAt = System.currentTimeMillis()
            )

            documentRepository.updateDocumentWithFields(updatedDoc, mergedFields)
            Log.d("HomeViewModel", "Online AI Field Extraction completed successfully")
        } catch (e: Exception) {
            Log.i("MyVaultE2E", "AI EXTRACTION FAILED, exception=${e.javaClass.name}: ${e.message}")
            Log.w("HomeViewModel", "AI Extraction failed/offline, falling back to LocalFieldExtractor: ${e.localizedMessage}")

            val isOffline = isConnectivityError(e)
            val finalStatus = if (isOffline) ExtractionStatus.OCR_COMPLETED else ExtractionStatus.AI_FAILED
            val errorMsg = if (isOffline) null else (e.localizedMessage ?: e.javaClass.simpleName)

            documentRepository.updateExtractionStatus(documentId, finalStatus, errorMsg)

            // Offline Fallback to LocalFieldExtractor if no user fields exist
            Log.i("MyVaultE2E", "USING LOCAL FALLBACK")
            val localResult = localFieldExtractor.extract(documentType, ocrText)
            val mergedFields = mergeFieldsPreservingUser(existingFields, localResult.fields)
            documentRepository.saveDocumentFields(documentId, mergedFields)
        }
    }

    private fun isConnectivityError(e: Exception): Boolean {
        return e is ConnectException ||
               e is UnknownHostException ||
               e is SocketTimeoutException ||
               e.cause is ConnectException ||
               e.cause is UnknownHostException ||
               e.cause is SocketTimeoutException
    }

    private fun mergeFieldsPreservingUser(
        existingFields: List<DocumentField>,
        newFields: List<DocumentField>
    ): List<DocumentField> {
        val userFields = existingFields.filter { it.source == FieldSource.USER }
        val userFieldNames = userFields.map { it.fieldName.lowercase().trim() }.toSet()

        val merged = mutableListOf<DocumentField>()
        merged.addAll(userFields)

        val consolidatedNewFields = AddressConsolidator.consolidate(newFields)

        for (newField in consolidatedNewFields) {
            if (!userFieldNames.contains(newField.fieldName.lowercase().trim())) {
                merged.add(newField)
            }
        }
        return merged
    }

    fun saveDocumentAndFields(document: Document, fields: List<DocumentField>, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            documentRepository.updateDocumentWithFields(document, fields)
            onSaved()
        }
    }

    fun deleteDocument(
        document: Document,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val fileDeleted = fileStorageManager.deleteFile(document.filePath)
            if (!fileDeleted) {
                onError("Unable to delete the document file. Please try again.")
                return@launch
            }

            try {
                documentRepository.deleteDocument(document)
                onSuccess()
            } catch (e: Exception) {
                onError("Unable to delete the document record: ${e.localizedMessage}")
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    class Factory(
        private val context: Context,
        private val repository: DocumentRepository,
        private val apiBaseUrl: String? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                val appContext = context.applicationContext
                val storageManager = LocalFileStorageManager(appContext)
                val ocrEngine = MlKitOcrEngine(appContext)
                val api = RetrofitClient.createApi(apiBaseUrl)
                val aiFieldExtractor = AiFieldExtractor(api)
                val localFieldExtractor = LocalFieldExtractor()

                return HomeViewModel(
                    repository,
                    storageManager,
                    ocrEngine,
                    aiFieldExtractor,
                    localFieldExtractor,
                    appContext
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
