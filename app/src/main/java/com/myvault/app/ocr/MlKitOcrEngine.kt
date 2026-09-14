package com.myvault.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitOcrEngine(private val context: Context) : OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognizeText(file: File, fileType: String): OcrResult = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) {
            return@withContext OcrResult.Failure("File does not exist or is empty: ${file.absolutePath}")
        }

        return@withContext try {
            if (fileType.equals("PDF", ignoreCase = true)) {
                processPdfFile(file)
            } else {
                processImageFile(file)
            }
        } catch (e: Exception) {
            OcrResult.Failure(e.localizedMessage ?: "OCR processing failed", e)
        }
    }

    private suspend fun processImageFile(file: File): OcrResult {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val result = processInputImage(image)
        val rawText = result.text.trim()
        return if (rawText.isNotEmpty()) {
            OcrResult.Success(rawText)
        } else {
            OcrResult.Failure("No readable text found in image")
        }
    }

    private suspend fun processPdfFile(file: File): OcrResult {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)

            val pageCount = pdfRenderer.pageCount
            if (pageCount == 0) {
                return OcrResult.Failure("PDF file has 0 pages")
            }

            val fullTextBuilder = StringBuilder()

            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                // Render at 2x density for optimal OCR quality
                val width = page.width * 2
                val height = page.height * 2
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                val pageTextResult = processInputImage(image)
                val pageText = pageTextResult.text.trim()

                bitmap.recycle()

                if (pageText.isNotEmpty()) {
                    if (fullTextBuilder.isNotEmpty()) {
                        fullTextBuilder.append("\n\n--- Page ${i + 1} ---\n\n")
                    } else if (pageCount > 1) {
                        fullTextBuilder.append("--- Page 1 ---\n\n")
                    }
                    fullTextBuilder.append(pageText)
                }
            }

            val finalPdfText = fullTextBuilder.toString().trim()
            return if (finalPdfText.isNotEmpty()) {
                OcrResult.Success(finalPdfText)
            } else {
                OcrResult.Failure("No readable text found in PDF")
            }
        } finally {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (_: Exception) {}
        }
    }

    private suspend fun processInputImage(image: InputImage): Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { textResult ->
                if (continuation.isActive) {
                    continuation.resume(textResult)
                }
            }
            .addOnFailureListener { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
    }
}
