package com.myvault.app.ocr

sealed class OcrResult {
    data class Success(val text: String) : OcrResult()
    data class Failure(val errorMessage: String, val exception: Throwable? = null) : OcrResult()
}
