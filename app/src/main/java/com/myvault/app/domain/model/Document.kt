package com.myvault.app.domain.model

data class Document(
    val id: Long = 0,
    val title: String = "",
    val displayName: String = title,
    val originalFileName: String = title,
    val category: String = "General",
    val documentType: String = "UNKNOWN",
    val documentTypeSource: String = "AI",
    val dateAddedTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = dateAddedTimestamp,
    val updatedAt: Long = dateAddedTimestamp,
    val filePath: String,
    val fileType: String,
    val mimeType: String = if (fileType.equals("PDF", ignoreCase = true)) "application/pdf" else "image/jpeg",
    val fileSize: Long = 0L,
    val ocrText: String? = null,
    val ocrStatus: OcrStatus = OcrStatus.PENDING,
    val extractionStatus: ExtractionStatus = ExtractionStatus.NOT_PROCESSED,
    val extractionError: String? = null,
    val thumbnailPath: String? = null
)
