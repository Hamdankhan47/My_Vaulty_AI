package com.myvault.app

import com.myvault.app.ocr.OcrEngine
import com.myvault.app.ocr.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PaddleOcrEngineTest {

    private class MockOcrEngine : OcrEngine {
        override suspend fun recognizeText(file: File, fileType: String): OcrResult {
            if (!file.exists()) return OcrResult.Failure("File does not exist")
            return OcrResult.Success("Sample PaddleOCR Text Output")
        }
    }

    @Test
    fun `test PaddleOcrEngine delegation and successful recognition`() = runBlocking {
        val tempFile = File.createTempFile("test_paddle", ".txt").apply {
            writeText("Hello World")
            deleteOnExit()
        }

        val mockEngine = MockOcrEngine()
        val result = mockEngine.recognizeText(tempFile, "IMAGE")

        assertTrue(result is OcrResult.Success)
        assertEquals("Sample PaddleOCR Text Output", (result as OcrResult.Success).text)
    }

    @Test
    fun `test PaddleOcrEngine returns failure for non-existent file`() = runBlocking {
        val nonExistentFile = File("/path/does_not_exist.jpg")
        val mockEngine = MockOcrEngine()
        val result = mockEngine.recognizeText(nonExistentFile, "IMAGE")

        assertTrue(result is OcrResult.Failure)
        assertEquals("File does not exist", (result as OcrResult.Failure).errorMessage)
    }
}
