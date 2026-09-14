package com.myvault.app.extraction

import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.FieldType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalFieldExtractor : FieldExtractor {

    override suspend fun extract(
        documentType: String?,
        ocrText: String
    ): ExtractionResult = withContext(Dispatchers.Default) {
        val currentDocType = documentType ?: "UNKNOWN"
        if (ocrText.isBlank()) return@withContext ExtractionResult(currentDocType, emptyList())

        val fields = mutableListOf<DocumentField>()
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotBlank() }

        val rules = listOf(
            Regex("(?i)^(?:Provider|Company|Organization)[:\\s]+(.+)") to ("Provider" to FieldType.TEXT),
            Regex("(?i)^(?:Customer Name|Customer|Name)[:\\s]+(.+)") to ("Customer Name" to FieldType.TEXT),
            Regex("(?i)^(?:Reference No|Reference Number|Ref No|Ref Number)[:\\s]+(.+)") to ("Reference Number" to FieldType.TEXT),
            Regex("(?i)^(?:Account No|Account Number|Acc No)[:\\s]+(.+)") to ("Account Number" to FieldType.TEXT),
            Regex("(?i)^(?:Amount|Current Bill|Total Amount|Payable)[:\\s]+(?:Rs\\.?|\\$)?\\s*([0-9.,]+)") to ("Amount" to FieldType.NUMBER),
            Regex("(?i)^(?:Due Date|Pay By|Expiry Date)[:\\s]+(.+)") to ("Due Date" to FieldType.DATE),
            Regex("(?i)^(?:Issue Date|Bill Date|Date)[:\\s]+(.+)") to ("Issue Date" to FieldType.DATE)
        )

        val foundNames = mutableSetOf<String>()

        for (line in lines) {
            for ((regex, info) in rules) {
                val (fieldName, fieldType) = info
                if (foundNames.contains(fieldName)) continue

                val match = regex.find(line)
                if (match != null && match.groupValues.size > 1) {
                    val value = match.groupValues[1].trim()
                    if (value.isNotEmpty()) {
                        fields.add(
                            DocumentField(
                                fieldName = fieldName,
                                fieldValue = value,
                                fieldType = fieldType,
                                source = FieldSource.OCR
                            )
                        )
                        foundNames.add(fieldName)
                    }
                }
            }
        }

        ExtractionResult(currentDocType, fields)
    }
}
