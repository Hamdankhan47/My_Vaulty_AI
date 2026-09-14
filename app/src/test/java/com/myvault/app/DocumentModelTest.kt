package com.myvault.app

import com.myvault.app.data.local.DocumentEntity
import com.myvault.app.data.local.toDomainModel
import com.myvault.app.data.local.toEntity
import com.myvault.app.domain.model.Document
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.ExtractionStatus
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.FieldType
import com.myvault.app.domain.model.OcrStatus
import com.myvault.app.domain.model.PredefinedDocumentTypes
import com.myvault.app.extraction.AddressConsolidator
import com.myvault.app.ui.detail.extractDocumentActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentModelTest {

    @Test
    fun `test document model supports two filenames and complete file metadata`() {
        val doc = Document(
            id = 10L,
            title = "September Electricity Bill",
            displayName = "September Electricity Bill",
            originalFileName = "IMG_20260914_102533.jpg",
            category = "Bill",
            documentType = "ELECTRICITY_BILL",
            dateAddedTimestamp = 1789382400000L,
            createdAt = 1789382400000L,
            updatedAt = 1789382410000L,
            filePath = "/storage/emulated/0/Android/data/com.myvault.app/files/myvault_documents/doc_1.jpg",
            fileType = "IMAGE",
            mimeType = "image/jpeg",
            fileSize = 204850L,
            ocrText = "LESCO ELECTRICITY BILL\nConsumer Name: Hamdan Khan\nReference No: 123456\nAmount Due: Rs. 4500\nDue Date: 20 September 2026",
            ocrStatus = OcrStatus.COMPLETED,
            extractionStatus = ExtractionStatus.AI_COMPLETED,
            extractionError = null,
            thumbnailPath = "/storage/emulated/0/Android/data/com.myvault.app/files/myvault_documents/thumb_1.jpg"
        )

        assertEquals("September Electricity Bill", doc.displayName)
        assertEquals("IMG_20260914_102533.jpg", doc.originalFileName)
        assertEquals("image/jpeg", doc.mimeType)
        assertEquals(204850L, doc.fileSize)
        assertEquals(ExtractionStatus.AI_COMPLETED, doc.extractionStatus)
        assertNull(doc.extractionError)
        assertEquals("/storage/emulated/0/Android/data/com.myvault.app/files/myvault_documents/thumb_1.jpg", doc.thumbnailPath)
        assertNotNull(doc.ocrText)
    }

    @Test
    fun `test DocumentEntity to domain model mapping and entity conversion`() {
        val entity = DocumentEntity(
            id = 5L,
            title = "Gas Bill August",
            displayName = "Gas Bill August",
            originalFileName = "scan_001.pdf",
            category = "Bill",
            documentType = "GAS_BILL",
            documentTypeSource = "AI",
            dateAddedTimestamp = 1789382400000L,
            createdAt = 1789382400000L,
            updatedAt = 1789382400000L,
            filePath = "/path/to/doc.pdf",
            fileType = "PDF",
            mimeType = "application/pdf",
            fileSize = 512000L,
            ocrText = "SSGC GAS BILL",
            ocrStatus = "COMPLETED",
            extractionStatus = "AI_FAILED",
            extractionError = "API Timeout",
            thumbnailPath = null
        )

        val domain = entity.toDomainModel()
        assertEquals("Gas Bill August", domain.displayName)
        assertEquals("scan_001.pdf", domain.originalFileName)
        assertEquals("application/pdf", domain.mimeType)
        assertEquals(512000L, domain.fileSize)
        assertEquals(ExtractionStatus.AI_FAILED, domain.extractionStatus)
        assertEquals("API Timeout", domain.extractionError)
        assertEquals("AI", domain.documentTypeSource)

        val backToEntity = domain.toEntity()
        assertEquals(entity.id, backToEntity.id)
        assertEquals(entity.displayName, backToEntity.displayName)
        assertEquals(entity.originalFileName, backToEntity.originalFileName)
        assertEquals(entity.extractionStatus, backToEntity.extractionStatus)
        assertEquals(entity.extractionError, backToEntity.extractionError)
        assertEquals(entity.documentTypeSource, backToEntity.documentTypeSource)
    }

    @Test
    fun `test field type supports TEXT, NUMBER, DATE, and CURRENCY`() {
        val field1 = DocumentField(fieldName = "Consumer Name", fieldValue = "Hamdan Khan", fieldType = FieldType.TEXT, source = FieldSource.AI)
        val field2 = DocumentField(fieldName = "Reference Number", fieldValue = "123456", fieldType = FieldType.TEXT, source = FieldSource.AI)
        val field3 = DocumentField(fieldName = "Amount Due", fieldValue = "Rs. 4,500", fieldType = FieldType.CURRENCY, source = FieldSource.USER)
        val field4 = DocumentField(fieldName = "Due Date", fieldValue = "2026-09-20", fieldType = FieldType.DATE, source = FieldSource.AI)

        assertEquals(FieldType.TEXT, field1.fieldType)
        assertEquals(FieldType.TEXT, field2.fieldType)
        assertEquals(FieldType.CURRENCY, field3.fieldType)
        assertEquals(FieldSource.USER, field3.source)
        assertEquals(FieldType.DATE, field4.fieldType)
    }

    @Test
    fun `test editing displayName does not alter originalFileName`() {
        val initialDoc = Document(
            id = 1L,
            title = "IMG_001.jpg",
            displayName = "IMG_001.jpg",
            originalFileName = "IMG_001.jpg",
            filePath = "/path/to/IMG_001.jpg",
            fileType = "IMAGE"
        )

        val updatedDoc = initialDoc.copy(
            title = "Electricity Bill September 2026",
            displayName = "Electricity Bill September 2026",
            updatedAt = System.currentTimeMillis()
        )

        assertEquals("Electricity Bill September 2026", updatedDoc.displayName)
        assertEquals("IMG_001.jpg", updatedDoc.originalFileName)
        assertEquals("/path/to/IMG_001.jpg", updatedDoc.filePath)
    }

    @Test
    fun `test AI button action label mapping for every ExtractionStatus`() {
        val getButtonText: (ExtractionStatus) -> String = { status ->
            when (status) {
                ExtractionStatus.NOT_PROCESSED, ExtractionStatus.OCR_COMPLETED -> "Process with AI"
                ExtractionStatus.AI_PROCESSING -> "Processing with AI..."
                ExtractionStatus.AI_COMPLETED -> "Reprocess with AI"
                ExtractionStatus.AI_FAILED -> "Retry AI"
            }
        }

        assertEquals("Process with AI", getButtonText(ExtractionStatus.NOT_PROCESSED))
        assertEquals("Process with AI", getButtonText(ExtractionStatus.OCR_COMPLETED))
        assertEquals("Processing with AI...", getButtonText(ExtractionStatus.AI_PROCESSING))
        assertEquals("Reprocess with AI", getButtonText(ExtractionStatus.AI_COMPLETED))
        assertEquals("Retry AI", getButtonText(ExtractionStatus.AI_FAILED))
    }

    @Test
    fun `test blank or whitespace display name validation`() {
        val isValidName: (String) -> Boolean = { name -> name.trim().isNotBlank() }

        assertFalse(isValidName(""))
        assertFalse(isValidName("   "))
        assertFalse(isValidName("\t\n"))
        assertTrue(isValidName("September Electricity Bill"))
    }

    @Test
    fun `test PredefinedDocumentTypes matching for predefined and custom document types`() {
        val electricityMatch = PredefinedDocumentTypes.normalizeAndMatch("Electricity Bill")
        assertEquals("Electricity Bill", electricityMatch.docType)
        assertEquals("Bill", electricityMatch.category)
        assertFalse(electricityMatch.isCustom)

        val bankMatch = PredefinedDocumentTypes.normalizeAndMatch("bank statement")
        assertEquals("Bank Statement", bankMatch.docType)
        assertEquals("Banking", bankMatch.category)
        assertFalse(bankMatch.isCustom)

        val customMatch = PredefinedDocumentTypes.normalizeAndMatch("Vehicle Registration Document")
        assertEquals("Vehicle Registration Document", customMatch.docType)
        assertEquals("Other", customMatch.category)
        assertTrue(customMatch.isCustom)
    }

    @Test
    fun `test AddressConsolidator combines street, city, and country into one ADDRESS field`() {
        val fields = listOf(
            DocumentField(fieldName = "Street", fieldValue = "123 Main Street", fieldType = FieldType.TEXT, source = FieldSource.AI),
            DocumentField(fieldName = "City", fieldValue = "Islamabad", fieldType = FieldType.TEXT, source = FieldSource.AI),
            DocumentField(fieldName = "Country", fieldValue = "Pakistan", fieldType = FieldType.TEXT, source = FieldSource.AI)
        )

        val consolidated = AddressConsolidator.consolidate(fields)
        assertEquals(1, consolidated.size)
        assertEquals("Address", consolidated[0].fieldName)
        assertEquals("123 Main Street, Islamabad, Pakistan", consolidated[0].fieldValue)
        assertEquals(FieldType.ADDRESS, consolidated[0].fieldType)
    }

    @Test
    fun `test AddressConsolidator deduplicates identical or overlapping address fields`() {
        val fields = listOf(
            DocumentField(fieldName = "Address", fieldValue = "123 Main Street, Islamabad", fieldType = FieldType.ADDRESS, source = FieldSource.AI),
            DocumentField(fieldName = "Customer Address", fieldValue = "123 Main Street, Islamabad", fieldType = FieldType.ADDRESS, source = FieldSource.AI),
            DocumentField(fieldName = "Location", fieldValue = "Islamabad", fieldType = FieldType.TEXT, source = FieldSource.AI)
        )

        val consolidated = AddressConsolidator.consolidate(fields)
        assertEquals(1, consolidated.size)
        assertEquals("Address", consolidated[0].fieldName)
        assertEquals("123 Main Street, Islamabad", consolidated[0].fieldValue)
        assertEquals(FieldType.ADDRESS, consolidated[0].fieldType)
    }

    @Test
    fun `test AddressConsolidator preserves genuinely distinct addresses`() {
        val fields = listOf(
            DocumentField(fieldName = "Billing Address", fieldValue = "123 Main Street, Islamabad", fieldType = FieldType.ADDRESS, source = FieldSource.AI),
            DocumentField(fieldName = "Office Address", fieldValue = "456 Blue Area, Lahore", fieldType = FieldType.ADDRESS, source = FieldSource.AI)
        )

        val consolidated = AddressConsolidator.consolidate(fields)
        assertEquals(2, consolidated.size)
        val billing = consolidated.find { it.fieldName == "Billing Address" }!!
        val office = consolidated.find { it.fieldName == "Office Address" }!!
        assertEquals("123 Main Street, Islamabad", billing.fieldValue)
        assertEquals("456 Blue Area, Lahore", office.fieldValue)
    }

    @Test
    fun `test document-level actions collection and deduplication`() {
        // Document with PHONE only -> exactly one Call action phone collected
        val phoneDocFields = listOf(
            DocumentField(fieldName = "Phone 1", fieldValue = "03001234567", fieldType = FieldType.PHONE, source = FieldSource.AI),
            DocumentField(fieldName = "Phone 2", fieldValue = "03009876543", fieldType = FieldType.PHONE, source = FieldSource.AI)
        )
        val phoneActions = extractDocumentActions(phoneDocFields)
        assertEquals("03001234567", phoneActions.phone)
        assertNull(phoneActions.email)
        assertNull(phoneActions.address)
        assertNull(phoneActions.url)
        assertTrue(phoneActions.hasAnyAction)

        // Document with EMAIL only -> exactly one Email action
        val emailDocFields = listOf(
            DocumentField(fieldName = "Email 1", fieldValue = "a@gmail.com", fieldType = FieldType.EMAIL, source = FieldSource.AI),
            DocumentField(fieldName = "Email 2", fieldValue = "b@gmail.com", fieldType = FieldType.EMAIL, source = FieldSource.AI)
        )
        val emailActions = extractDocumentActions(emailDocFields)
        assertEquals("a@gmail.com", emailActions.email)
        assertTrue(emailActions.hasAnyAction)

        // Document with ADDRESS only -> exactly one Address action
        val addressDocFields = listOf(
            DocumentField(fieldName = "Address", fieldValue = "Islamabad, Pakistan", fieldType = FieldType.ADDRESS, source = FieldSource.AI)
        )
        val addressActions = extractDocumentActions(addressDocFields)
        assertEquals("Islamabad, Pakistan", addressActions.address)
        assertTrue(addressActions.hasAnyAction)

        // Document with URL only -> exactly one Open Link action
        val urlDocFields = listOf(
            DocumentField(fieldName = "Website 1", fieldValue = "https://abc.com", fieldType = FieldType.URL, source = FieldSource.AI),
            DocumentField(fieldName = "Website 2", fieldValue = "https://xyz.com", fieldType = FieldType.URL, source = FieldSource.AI)
        )
        val urlActions = extractDocumentActions(urlDocFields)
        assertEquals("https://abc.com", urlActions.url)
        assertTrue(urlActions.hasAnyAction)

        // Document with PERSON_NAME + PHONE + EMAIL -> Save Contact, Call, Email actions
        val contactDocFields = listOf(
            DocumentField(fieldName = "Person Name", fieldValue = "Ali Khan", fieldType = FieldType.PERSON_NAME, source = FieldSource.AI),
            DocumentField(fieldName = "Phone", fieldValue = "03001234567", fieldType = FieldType.PHONE, source = FieldSource.AI),
            DocumentField(fieldName = "Email", fieldValue = "ali@gmail.com", fieldType = FieldType.EMAIL, source = FieldSource.AI)
        )
        val contactActions = extractDocumentActions(contactDocFields)
        assertEquals("Ali Khan", contactActions.contactName)
        assertEquals("03001234567", contactActions.phone)
        assertEquals("ali@gmail.com", contactActions.email)
        assertTrue(contactActions.canSaveContact)
        assertTrue(contactActions.hasAnyAction)

        // Document with only normal fields -> no actions
        val normalDocFields = listOf(
            DocumentField(fieldName = "Bill Number", fieldValue = "12345", fieldType = FieldType.TEXT, source = FieldSource.AI),
            DocumentField(fieldName = "Reference Number", fieldValue = "98765", fieldType = FieldType.TEXT, source = FieldSource.AI),
            DocumentField(fieldName = "Amount", fieldValue = "4500", fieldType = FieldType.NUMBER, source = FieldSource.AI),
            DocumentField(fieldName = "Due Date", fieldValue = "2026-09-20", fieldType = FieldType.DATE, source = FieldSource.AI)
        )
        val normalActions = extractDocumentActions(normalDocFields)
        assertFalse(normalActions.hasAnyAction)
        assertFalse(normalActions.canSaveContact)
    }
}
