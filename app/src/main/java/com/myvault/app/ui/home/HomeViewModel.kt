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
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.OcrStatus
import com.myvault.app.domain.repository.DocumentRepository
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HomeViewModel(
    private val documentRepository: DocumentRepository,
    private val fileStorageManager: LocalFileStorageManager,
    private val ocrEngine: OcrEngine,
    private val aiFieldExtractor: FieldExtractor,
    private val localFieldExtractor: FieldExtractor
) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

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

    fun getDocumentFlow(id: Long): Flow<Document?> {
        return documentRepository.getDocumentByIdFlow(id)
    }

    fun getDocumentFieldsFlow(documentId: Long): Flow<List<DocumentField>> {
        return documentRepository.getDocumentFieldsFlow(documentId)
    }

    fun importDocument(
        uri: Uri,
        fileType: String,
        customCategory: String = "General",
        documentType: String = "UNKNOWN"
    ) {
        viewModelScope.launch {
            _isImporting.value = true
            _errorMessage.value = null

            val saveResult = fileStorageManager.saveFileFromUri(uri, fileType)
            saveResult.fold(
                onSuccess = { savedFile ->
                    val document = Document(
                        title = savedFile.fileName,
                        category = customCategory,
                        documentType = documentType,
                        dateAddedTimestamp = System.currentTimeMillis(),
                        filePath = savedFile.filePath,
                        fileType = fileType.uppercase(),
                        ocrStatus = OcrStatus.PROCESSING
                    )
                    val documentId = documentRepository.insertDocument(document)
                    _isImporting.value = false

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
            }
            return
        }
        runOcrForDocument(document.id, document.filePath, document.fileType, document.documentType)
    }

    private fun runOcrForDocument(documentId: Long, filePath: String, fileType: String, documentType: String) {
        viewModelScope.launch {
            documentRepository.updateOcrStatus(documentId, OcrStatus.PROCESSING)
            val file = File(filePath)
            if (!file.exists()) {
                documentRepository.updateOcrResult(documentId, OcrStatus.FAILED, null)
                return@launch
            }

            when (val result = ocrEngine.recognizeText(file, fileType)) {
                is OcrResult.Success -> {
                    Log.i("MyVaultE2E", "OCR SUCCESS, documentId=$documentId, textLength=${result.text.length}")
                    documentRepository.updateOcrResult(documentId, OcrStatus.COMPLETED, result.text)
                    Log.i("MyVaultE2E", "CALLING extractFields")
                    extractFields(documentId, documentType, result.text)
                }
                is OcrResult.Failure -> {
                    documentRepository.updateOcrResult(documentId, OcrStatus.FAILED, null)
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

        try {
            // Attempt Online AI Extraction via FastAPI + Gemini
            Log.i("MyVaultE2E", "CALLING AiFieldExtractor")
            val aiResult = aiFieldExtractor.extract(documentType, ocrText)
            val mergedFields = mergeFieldsPreservingUser(existingFields, aiResult.fields)

            val updatedDoc = if (existingDocument.documentType == "UNKNOWN" && aiResult.inferredDocumentType != "UNKNOWN") {
                existingDocument.copy(documentType = aiResult.inferredDocumentType)
            } else {
                existingDocument
            }

            documentRepository.updateDocumentWithFields(updatedDoc, mergedFields)
            Log.d("HomeViewModel", "Online AI Field Extraction completed successfully")
        } catch (e: Exception) {
            Log.i("MyVaultE2E", "AI EXTRACTION FAILED, exception=${e.javaClass.name}: ${e.message}")
            Log.w("HomeViewModel", "AI Extraction failed/offline, falling back to LocalFieldExtractor: ${e.localizedMessage}")

            // Offline Fallback to LocalFieldExtractor
            Log.i("MyVaultE2E", "USING LOCAL FALLBACK")
            val localResult = localFieldExtractor.extract(documentType, ocrText)
            val mergedFields = mergeFieldsPreservingUser(existingFields, localResult.fields)
            documentRepository.saveDocumentFields(documentId, mergedFields)
        }
    }

    private fun mergeFieldsPreservingUser(
        existingFields: List<DocumentField>,
        newFields: List<DocumentField>
    ): List<DocumentField> {
        val userFields = existingFields.filter { it.source == FieldSource.USER }
        val userFieldNames = userFields.map { it.fieldName.lowercase().trim() }.toSet()

        val merged = mutableListOf<DocumentField>()
        merged.addAll(userFields)

        for (newField in newFields) {
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
                    localFieldExtractor
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
