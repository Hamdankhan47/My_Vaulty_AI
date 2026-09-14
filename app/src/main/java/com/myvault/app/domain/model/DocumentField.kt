package com.myvault.app.domain.model

data class DocumentField(
    val id: Long = 0,
    val documentId: Long = 0,
    val fieldName: String,
    val fieldValue: String,
    val fieldType: FieldType = FieldType.TEXT,
    val source: FieldSource = FieldSource.OCR
)
