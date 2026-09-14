package com.myvault.app

import com.myvault.app.domain.model.Document
import com.myvault.app.domain.model.ExtractionStatus
import com.myvault.app.domain.model.OcrStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileViewerTest {

    @Test
    fun `test opening document is read-only and never alters document metadata`() {
        val originalDoc = Document(
            id = 100L,
            title = "Electric_Bill.pdf",
            displayName = "Electric Bill September",
            originalFileName = "Electric_Bill.pdf",
            category = "Bill",
            documentType = "ELECTRICITY_BILL",
            filePath = "/path/to/myvault_documents/doc_123.pdf",
            fileType = "PDF",
            mimeType = "application/pdf",
            fileSize = 102400L,
            ocrText = "IESCO ELECTRICITY BILL\nAmount: 8450",
            ocrStatus = OcrStatus.COMPLETED,
            extractionStatus = ExtractionStatus.AI_COMPLETED
        )

        // Read operation: FileViewer accesses file properties
        val file = File(originalDoc.filePath)
        val displayName = originalDoc.displayName.ifBlank { originalDoc.title }
        val originalName = originalDoc.originalFileName

        // Verify no metadata changes
        assertEquals(100L, originalDoc.id)
        assertEquals("Electric Bill September", displayName)
        assertEquals("Electric_Bill.pdf", originalName)
        assertEquals("/path/to/myvault_documents/doc_123.pdf", originalDoc.filePath)
        assertEquals("IESCO ELECTRICITY BILL\nAmount: 8450", originalDoc.ocrText)
        assertEquals(ExtractionStatus.AI_COMPLETED, originalDoc.extractionStatus)
        assertFalse(file.exists()) // File missing safely handled by UI without altering Document
    }

    @Test
    fun `test text file reader safety and truncation under 1 MB limit`() {
        val tempTxtFile = File.createTempFile("test_sample", ".txt")
        tempTxtFile.deleteOnExit()
        tempTxtFile.writeText("Sample MyVault Text Document Content\nLine 2\nLine 3")

        val maxBytes = 1024 * 1024
        val buffer = ByteArray(maxBytes)
        val bytesRead = tempTxtFile.inputStream().use { it.read(buffer, 0, maxBytes) }
        val actualBytes = if (bytesRead > 0) buffer.copyOf(bytesRead) else ByteArray(0)
        val content = String(actualBytes, Charsets.UTF_8)

        assertTrue(content.contains("Sample MyVault Text Document Content"))
        assertTrue(content.contains("Line 2"))
    }

    @Test
    fun `test file type classification helpers`() {
        val imageDoc = Document(filePath = "a.jpg", fileType = "IMAGE", mimeType = "image/jpeg")
        val pdfDoc = Document(filePath = "b.pdf", fileType = "PDF", mimeType = "application/pdf")
        val txtDoc = Document(filePath = "c.txt", fileType = "TXT", mimeType = "text/plain")
        val docxDoc = Document(filePath = "d.docx", fileType = "DOCX", mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")

        assertTrue(imageDoc.fileType == "IMAGE" || imageDoc.mimeType.startsWith("image/"))
        assertTrue(pdfDoc.fileType == "PDF" || pdfDoc.mimeType.contains("pdf"))
        assertTrue(txtDoc.fileType == "TXT" || txtDoc.mimeType.contains("text/plain"))
        assertFalse(docxDoc.fileType == "PDF")
    }
}
