package com.myvault.app

import android.content.ContextWrapper
import android.net.Uri
import com.myvault.app.data.local.LocalFileStorageManager
import com.myvault.app.domain.model.Document
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.ExtractionStatus
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.FieldType
import com.myvault.app.domain.model.OcrStatus
import com.myvault.app.domain.repository.DocumentRepository
import com.myvault.app.extraction.ExtractionResult
import com.myvault.app.extraction.FieldExtractor
import com.myvault.app.ocr.OcrEngine
import com.myvault.app.ocr.OcrResult
import com.myvault.app.ui.home.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
class PipelineAndRetryTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeDocumentRepository : DocumentRepository {
        val documents = mutableMapOf<Long, Document>()
        val fieldsMap = mutableMapOf<Long, MutableList<DocumentField>>()
        private var idCounter = 1L

        override fun getRecentDocuments(): Flow<List<Document>> = flowOf(documents.values.toList())
        override suspend fun getDocumentById(id: Long): Document? = documents[id]
        override fun getDocumentByIdFlow(id: Long): Flow<Document?> = flowOf(documents[id])

        override suspend fun insertDocument(document: Document): Long {
            val docId = if (document.id == 0L) idCounter++ else document.id
            val savedDoc = document.copy(id = docId)
            documents[docId] = savedDoc
            return docId
        }

        override suspend fun updateDocumentDisplayName(documentId: Long, displayName: String) {
            documents[documentId]?.let {
                documents[documentId] = it.copy(displayName = displayName, title = displayName, updatedAt = System.currentTimeMillis())
            }
        }

        override suspend fun updateDocumentType(documentId: Long, documentType: String, category: String, source: String) {
            documents[documentId]?.let {
                documents[documentId] = it.copy(documentType = documentType, category = category, documentTypeSource = source, updatedAt = System.currentTimeMillis())
            }
        }

        override suspend fun updateOcrResult(documentId: Long, status: OcrStatus, ocrText: String?) {
            documents[documentId]?.let {
                documents[documentId] = it.copy(ocrStatus = status, ocrText = ocrText)
            }
        }

        override suspend fun updateOcrStatus(documentId: Long, status: OcrStatus) {
            documents[documentId]?.let {
                documents[documentId] = it.copy(ocrStatus = status)
            }
        }

        override suspend fun updateExtractionStatus(documentId: Long, status: ExtractionStatus, error: String?) {
            documents[documentId]?.let {
                documents[documentId] = it.copy(extractionStatus = status, extractionError = error)
            }
        }

        override suspend fun deleteDocument(document: Document) {
            documents.remove(document.id)
            fieldsMap.remove(document.id)
        }

        override fun getDocumentFieldsFlow(documentId: Long): Flow<List<DocumentField>> {
            return flowOf(fieldsMap[documentId] ?: emptyList())
        }

        override suspend fun getDocumentFields(documentId: Long): List<DocumentField> {
            return fieldsMap[documentId] ?: emptyList()
        }

        override suspend fun saveDocumentFields(documentId: Long, fields: List<DocumentField>) {
            fieldsMap[documentId] = fields.map { it.copy(documentId = documentId) }.toMutableList()
        }

        override suspend fun updateDocumentWithFields(document: Document, fields: List<DocumentField>) {
            insertDocument(document)
            saveDocumentFields(document.id, fields)
        }
    }

    private class FakeOcrEngine(var ocrToReturn: OcrResult) : OcrEngine {
        var callCount = 0
        override suspend fun recognizeText(file: File, fileType: String): OcrResult {
            callCount++
            return ocrToReturn
        }
    }

    private class FakeFieldExtractor(var resultToReturn: Result<ExtractionResult>) : FieldExtractor {
        var callCount = 0
        override suspend fun extract(documentType: String?, ocrText: String): ExtractionResult {
            callCount++
            return resultToReturn.getOrThrow()
        }
    }

    private class DummyContext : ContextWrapper(null)

    private class FakeStorageManager : LocalFileStorageManager(DummyContext()) {
        override suspend fun saveFileFromUri(uri: Uri, fileType: String): Result<SavedFileResult> {
            return Result.success(
                SavedFileResult(
                    filePath = "/path/to/test_file.jpg",
                    fileName = "test_file.jpg",
                    fileSize = 102400L,
                    mimeType = "image/jpeg"
                )
            )
        }

        override fun deleteFile(filePath: String): Boolean = true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test offline OCR completes and saves raw OCR text with OCR_COMPLETED status`() = runBlocking {
        val tempFile = File.createTempFile("test_bill", ".jpg")
        tempFile.deleteOnExit()

        val repository = FakeDocumentRepository()
        val ocrEngine = FakeOcrEngine(OcrResult.Success("LESCO Electricity Bill\nAmount: 4500"))
        val aiExtractor = FakeFieldExtractor(Result.failure(ConnectException("No Internet Connection")))
        val localExtractor = FakeFieldExtractor(
            Result.success(
                ExtractionResult(
                    "ELECTRICITY_BILL",
                    listOf(DocumentField(fieldName = "Amount", fieldValue = "4500", fieldType = FieldType.NUMBER, source = FieldSource.OCR))
                )
            )
        )
        val storageManager = FakeStorageManager()

        val viewModel = HomeViewModel(repository, storageManager, ocrEngine, aiExtractor, localExtractor, DummyContext())

        val docId = repository.insertDocument(
            Document(
                title = "Bill.jpg",
                filePath = tempFile.absolutePath,
                fileType = "IMAGE"
            )
        )

        // Run OCR and extraction pipeline
        viewModel.retryOcr(repository.getDocumentById(docId)!!)

        val updatedDoc = repository.getDocumentById(docId)!!
        assertEquals(OcrStatus.COMPLETED, updatedDoc.ocrStatus)
        assertEquals("LESCO Electricity Bill\nAmount: 4500", updatedDoc.ocrText)
        assertEquals(ExtractionStatus.OCR_COMPLETED, updatedDoc.extractionStatus)
        assertNull(updatedDoc.extractionError)

        val fields = repository.getDocumentFields(docId)
        assertEquals(1, fields.size)
        assertEquals("Amount", fields[0].fieldName)
        assertEquals(FieldSource.OCR, fields[0].source)
    }

    @Test
    fun `test retry AI extraction reuses stored OCR text without re-running OCR`() = runBlocking {
        val tempFile = File.createTempFile("test_iesco", ".pdf")
        tempFile.deleteOnExit()

        val repository = FakeDocumentRepository()
        val ocrEngine = FakeOcrEngine(OcrResult.Success("Initial OCR text"))
        val aiExtractor = FakeFieldExtractor(
            Result.success(
                ExtractionResult(
                    "ELECTRICITY_BILL",
                    listOf(
                        DocumentField(fieldName = "Provider", fieldValue = "IESCO", fieldType = FieldType.TEXT, source = FieldSource.AI),
                        DocumentField(fieldName = "Amount", fieldValue = "8450", fieldType = FieldType.NUMBER, source = FieldSource.AI)
                    )
                )
            )
        )
        val localExtractor = FakeFieldExtractor(Result.success(ExtractionResult("UNKNOWN", emptyList())))
        val storageManager = FakeStorageManager()

        val viewModel = HomeViewModel(repository, storageManager, ocrEngine, aiExtractor, localExtractor, DummyContext())

        val docId = repository.insertDocument(
            Document(
                title = "IESCO.pdf",
                filePath = tempFile.absolutePath,
                fileType = "PDF",
                ocrText = "Stored Raw OCR Text: Reference 12345",
                ocrStatus = OcrStatus.COMPLETED,
                extractionStatus = ExtractionStatus.OCR_COMPLETED
            )
        )

        val initialOcrCalls = ocrEngine.callCount

        // Execute retry AI extraction directly on stored OCR text
        val retryResult = viewModel.executeAiExtractionRetry(docId)

        assertTrue(retryResult.isSuccess)
        // Verify ML Kit OCR engine was NOT called again
        assertEquals(initialOcrCalls, ocrEngine.callCount)

        val updatedDoc = repository.getDocumentById(docId)!!
        assertEquals(ExtractionStatus.AI_COMPLETED, updatedDoc.extractionStatus)
        assertNull(updatedDoc.extractionError)

        val fields = repository.getDocumentFields(docId)
        assertEquals(2, fields.size)
        assertEquals("Provider", fields[0].fieldName)
        assertEquals(FieldSource.AI, fields[0].source)
    }

    @Test
    fun `test retry replaces old fields while preserving USER fields`() = runBlocking {
        val tempFile = File.createTempFile("test_iesco2", ".pdf")
        tempFile.deleteOnExit()

        val repository = FakeDocumentRepository()
        val ocrEngine = FakeOcrEngine(OcrResult.Success("Stored OCR"))
        val aiExtractor = FakeFieldExtractor(
            Result.success(
                ExtractionResult(
                    "ELECTRICITY_BILL",
                    listOf(
                        DocumentField(fieldName = "Provider", fieldValue = "IESCO_NEW", fieldType = FieldType.TEXT, source = FieldSource.AI),
                        DocumentField(fieldName = "Amount", fieldValue = "9999", fieldType = FieldType.NUMBER, source = FieldSource.AI)
                    )
                )
            )
        )
        val localExtractor = FakeFieldExtractor(Result.success(ExtractionResult("UNKNOWN", emptyList())))
        val storageManager = FakeStorageManager()

        val viewModel = HomeViewModel(repository, storageManager, ocrEngine, aiExtractor, localExtractor, DummyContext())

        val docId = repository.insertDocument(
            Document(
                title = "IESCO.pdf",
                filePath = tempFile.absolutePath,
                fileType = "PDF",
                ocrText = "Stored Raw OCR Text",
                ocrStatus = OcrStatus.COMPLETED,
                extractionStatus = ExtractionStatus.AI_COMPLETED
            )
        )

        // Seed document with a USER field and an old AI field
        repository.saveDocumentFields(
            docId,
            listOf(
                DocumentField(fieldName = "Amount", fieldValue = "8450", fieldType = FieldType.NUMBER, source = FieldSource.USER),
                DocumentField(fieldName = "Provider", fieldValue = "OLD_PROVIDER", fieldType = FieldType.TEXT, source = FieldSource.AI)
            )
        )

        // Execute AI extraction retry
        val retryResult = viewModel.executeAiExtractionRetry(docId)
        assertTrue(retryResult.isSuccess)

        val fields = repository.getDocumentFields(docId)
        assertEquals(2, fields.size)

        // USER field Amount = 8450 must be preserved
        val amountField = fields.find { it.fieldName == "Amount" }!!
        assertEquals("8450", amountField.fieldValue)
        assertEquals(FieldSource.USER, amountField.source)

        // AI field Provider = IESCO_NEW replaced old AI provider
        val providerField = fields.find { it.fieldName == "Provider" }!!
        assertEquals("IESCO_NEW", providerField.fieldValue)
        assertEquals(FieldSource.AI, providerField.source)
    }

    @Test
    fun `test USER manual document type selection is protected during AI reprocess`() = runBlocking {
        val tempFile = File.createTempFile("test_protection", ".jpg")
        tempFile.deleteOnExit()

        val repository = FakeDocumentRepository()
        val ocrEngine = FakeOcrEngine(OcrResult.Success("Sample OCR"))
        val aiExtractor = FakeFieldExtractor(
            Result.success(
                ExtractionResult(
                    inferredDocumentType = "Electricity Bill",
                    category = "Bill",
                    fields = emptyList()
                )
            )
        )
        val localExtractor = FakeFieldExtractor(Result.success(ExtractionResult("UNKNOWN", emptyList())))
        val storageManager = FakeStorageManager()

        val viewModel = HomeViewModel(repository, storageManager, ocrEngine, aiExtractor, localExtractor, DummyContext())

        // Insert document with USER documentTypeSource
        val docId = repository.insertDocument(
            Document(
                title = "Car Registration",
                filePath = tempFile.absolutePath,
                fileType = "IMAGE",
                ocrText = "Vehicle Registration Information",
                documentType = "Vehicle Registration Document",
                category = "Other",
                documentTypeSource = "USER",
                ocrStatus = OcrStatus.COMPLETED,
                extractionStatus = ExtractionStatus.OCR_COMPLETED
            )
        )

        // Run AI reprocess
        val retryResult = viewModel.executeAiExtractionRetry(docId)
        assertTrue(retryResult.isSuccess)

        val updatedDoc = repository.getDocumentById(docId)!!
        // USER-selected documentType and category MUST BE PROTECTED from AI replacement
        assertEquals("Vehicle Registration Document", updatedDoc.documentType)
        assertEquals("Other", updatedDoc.category)
        assertEquals("USER", updatedDoc.documentTypeSource)
    }

    @Test
    fun `test reprocess OCR replaces OCR text using physical file without calling AI`() = runBlocking {
        val tempFile = File.createTempFile("test_reprocess_ocr", ".jpg")
        tempFile.deleteOnExit()

        val repository = FakeDocumentRepository()
        val ocrEngine = FakeOcrEngine(OcrResult.Success("Newly Reprocessed OCR Text 2026"))
        val aiExtractor = FakeFieldExtractor(Result.success(ExtractionResult("UNKNOWN", emptyList())))
        val localExtractor = FakeFieldExtractor(Result.success(ExtractionResult("UNKNOWN", emptyList())))
        val storageManager = FakeStorageManager()

        val viewModel = HomeViewModel(repository, storageManager, ocrEngine, aiExtractor, localExtractor, DummyContext())

        val docId = repository.insertDocument(
            Document(
                title = "Doc.jpg",
                filePath = tempFile.absolutePath,
                fileType = "IMAGE",
                ocrText = "Old Initial OCR Text",
                ocrStatus = OcrStatus.COMPLETED,
                extractionStatus = ExtractionStatus.AI_COMPLETED
            )
        )

        // Seed document with a USER field and an AI field
        repository.saveDocumentFields(
            docId,
            listOf(
                DocumentField(fieldName = "Customer Name", fieldValue = "Hamdan", fieldType = FieldType.PERSON_NAME, source = FieldSource.USER),
                DocumentField(fieldName = "Old AI Field", fieldValue = "Val", fieldType = FieldType.TEXT, source = FieldSource.AI)
            )
        )

        val initialAiCalls = aiExtractor.callCount

        // Reprocess OCR explicitly
        var callbackSuccess = false
        viewModel.reprocessOcr(repository.getDocumentById(docId)!!) { res ->
            callbackSuccess = res.isSuccess
        }

        assertTrue(callbackSuccess)
        val updatedDoc = repository.getDocumentById(docId)!!

        // Verify OCR text updated
        assertEquals("Newly Reprocessed OCR Text 2026", updatedDoc.ocrText)
        assertEquals(OcrStatus.COMPLETED, updatedDoc.ocrStatus)
        assertEquals(ExtractionStatus.OCR_COMPLETED, updatedDoc.extractionStatus)

        // Verify Gemini AI extractor was NOT automatically called
        assertEquals(initialAiCalls, aiExtractor.callCount)

        // Verify USER field preserved while old AI field cleared
        val fields = repository.getDocumentFields(docId)
        assertEquals(1, fields.size)
        assertEquals("Customer Name", fields[0].fieldName)
        assertEquals(FieldSource.USER, fields[0].source)
    }

    @Test
    fun `test reprocess OCR preserves previous valid OCR text when new OCR fails`() = runBlocking {
        val tempFile = File.createTempFile("test_ocr_failed", ".jpg")
        tempFile.deleteOnExit()

        val repository = FakeDocumentRepository()
        val ocrEngine = FakeOcrEngine(OcrResult.Failure("ML Kit OCR error"))
        val aiExtractor = FakeFieldExtractor(Result.success(ExtractionResult("UNKNOWN", emptyList())))
        val localExtractor = FakeFieldExtractor(Result.success(ExtractionResult("UNKNOWN", emptyList())))
        val storageManager = FakeStorageManager()

        val viewModel = HomeViewModel(repository, storageManager, ocrEngine, aiExtractor, localExtractor, DummyContext())

        val docId = repository.insertDocument(
            Document(
                title = "Doc.jpg",
                filePath = tempFile.absolutePath,
                fileType = "IMAGE",
                ocrText = "Original Valid OCR Text",
                ocrStatus = OcrStatus.COMPLETED,
                extractionStatus = ExtractionStatus.AI_COMPLETED
            )
        )

        var callbackFailure = false
        viewModel.reprocessOcr(repository.getDocumentById(docId)!!) { res ->
            callbackFailure = res.isFailure
        }

        assertTrue(callbackFailure)
        val docAfterFailure = repository.getDocumentById(docId)!!

        // Valid previous OCR text MUST be preserved
        assertEquals("Original Valid OCR Text", docAfterFailure.ocrText)
        assertEquals(OcrStatus.COMPLETED, docAfterFailure.ocrStatus)
    }
}
