package com.myvault.app.extraction

import com.myvault.app.domain.model.DocumentField

data class ExtractionResult(
    val inferredDocumentType: String,
    val fields: List<DocumentField>,
    val category: String? = null
)
