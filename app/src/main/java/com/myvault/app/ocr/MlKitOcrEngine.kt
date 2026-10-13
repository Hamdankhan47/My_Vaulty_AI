package com.myvault.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
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
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
        val image = if (originalBitmap != null) {
            val processedBitmap = preprocessImageBitmap(originalBitmap)
            InputImage.fromBitmap(processedBitmap, 0)
        } else {
            InputImage.fromFilePath(context, Uri.fromFile(file))
        }

        val result = processInputImage(image)
        val orderedText = sortTextByReadingOrder(result)
        return if (orderedText.isNotEmpty()) {
            OcrResult.Success(orderedText)
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
                // Render at 300 DPI (300 / 72 points per inch = 4.1667x scale)
                val scaleFactor = 300f / 72f
                val width = (page.width * scaleFactor).toInt().coerceAtLeast(1)
                val height = (page.height * scaleFactor).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val image = InputImage.fromBitmap(bitmap, 0)
                val pageTextResult = processInputImage(image)
                val pageText = sortTextByReadingOrder(pageTextResult)

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

    private fun sortTextByReadingOrder(textResult: Text): String {
        val allLines = textResult.textBlocks.flatMap { it.lines }
        if (allLines.isEmpty()) return textResult.text.trim()

        val sortedLines = allLines.sortedWith { l1, l2 ->
            val b1 = l1.boundingBox
            val b2 = l2.boundingBox
            if (b1 == null || b2 == null) return@sortedWith 0

            val sameColumn = Math.abs(b1.left - b2.left) < 120 || (b1.right > b2.left && b1.left < b2.right)
            if (sameColumn) {
                b1.top.compareTo(b2.top)
            } else {
                b1.left.compareTo(b2.left)
            }
        }

        return sortedLines.joinToString("\n") { it.text.trim() }.trim()
    }

    private fun preprocessImageBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val processedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(processedBitmap)
        val paint = Paint()

        // Light contrast enhancement for camera scans (1.25x contrast, slight brightness offset)
        val contrast = 1.25f
        val brightness = -10f
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return processedBitmap
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
