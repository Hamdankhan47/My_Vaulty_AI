package com.myvault.app.domain.model

data class Document(
    val id: Long = 0,
    val title: String,
    val category: String = "General",
    val documentType: String = "UNKNOWN",
    val dateAddedTimestamp: Long = System.currentTimeMillis(),
    val filePath: String,
    val fileType: String,
    val ocrText: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.PENDING
)
