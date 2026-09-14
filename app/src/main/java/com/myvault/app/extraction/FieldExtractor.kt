package com.myvault.app.extraction

interface FieldExtractor {
    suspend fun extract(
        documentType: String?,
        ocrText: String
    ): ExtractionResult
}
