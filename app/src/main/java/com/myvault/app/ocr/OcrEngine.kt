package com.myvault.app.ocr

import java.io.File

interface OcrEngine {
    suspend fun recognizeText(file: File, fileType: String): OcrResult
}
