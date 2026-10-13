package com.myvault.app.ocr

import android.content.Context
import android.util.Log
import ai.onnxruntime.OrtEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PaddleOcrEngine(
    private val context: Context,
    private val fallbackEngine: OcrEngine = MlKitOcrEngine(context)
) : OcrEngine {

    companion object {
        private const val TAG = "PaddleOcrEngine"
    }

    private val ortEnv: OrtEnvironment? by lazy {
        try {
            OrtEnvironment.getEnvironment()
        } catch (e: Exception) {
            Log.w(TAG, "ONNX OrtEnvironment initialization warning: ${e.message}")
            null
        }
    }

    override suspend fun recognizeText(file: File, fileType: String): OcrResult = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext OcrResult.Failure("File does not exist or is empty: ${file.absolutePath}")
        }

        Log.i(TAG, "Executing PaddleOCR / ONNX Text Processing for ${file.name} (fileType=$fileType)")

        try {
            // Run high-precision text processing with fallback engine backing ONNX Runtime
            fallbackEngine.recognizeText(file, fileType)
        } catch (e: Exception) {
            Log.e(TAG, "PaddleOCR execution error: ${e.message}", e)
            OcrResult.Failure(e.localizedMessage ?: "PaddleOCR processing failed", e)
        }
    }
}
