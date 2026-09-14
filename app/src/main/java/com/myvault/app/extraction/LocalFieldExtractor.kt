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
            Regex("(?i)^(?:Phone|Mobile|Contact No|Tel)[:\\s]+(.+)") to ("Phone Number" to FieldType.PHONE),
            Regex("(?i)^(?:Email|E-mail)[:\\s]+(.+)") to ("Email Address" to FieldType.EMAIL),
            Regex("(?i)^(?:Website|Site|URL|Link)[:\\s]+(.+)") to ("Website" to FieldType.URL),
            Regex("(?i)^(?:Address|Office Address|Location)[:\\s]+(.+)") to ("Address" to FieldType.ADDRESS),
            Regex("(?i)^(?:Provider|Company|Organization)[:\\s]+(.+)") to ("Organization" to FieldType.ORGANIZATION),
            Regex("(?i)^(?:Contact Person|Person Name)[:\\s]+(.+)") to ("Contact Person" to FieldType.PERSON_NAME),
            Regex("(?i)^(?:Customer Name|Customer|Name)[:\\s]+(.+)") to ("Customer Name" to FieldType.PERSON_NAME),
            Regex("(?i)^(?:Reference No|Reference Number|Ref No|Ref Number)[:\\s]+(.+)") to ("Reference Number" to FieldType.TEXT),
            Regex("(?i)^(?:Account No|Account Number|Acc No)[:\\s]+(.+)") to ("Account Number" to FieldType.TEXT),
            Regex("(?i)^(?:Amount|Current Bill|Total Amount|Payable)[:\\s]+(?:Rs\\.?|\\$)?\\s*([0-9.,]+)") to ("Amount" to FieldType.CURRENCY),
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

        // Lightweight fallback detection for raw email/url/phone if not already captured by rules
        if (!foundNames.contains("Email Address")) {
            val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
            val emailMatch = emailRegex.find(ocrText)
            if (emailMatch != null) {
                fields.add(
                    DocumentField(
                        fieldName = "Email",
                        fieldValue = emailMatch.value,
                        fieldType = FieldType.EMAIL,
                        source = FieldSource.OCR
                    )
                )
            }
        }

        if (!foundNames.contains("Website")) {
            val urlRegex = Regex("(?:https?://|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?")
            val urlMatch = urlRegex.find(ocrText)
            if (urlMatch != null) {
                fields.add(
                    DocumentField(
                        fieldName = "Website",
                        fieldValue = urlMatch.value,
                        fieldType = FieldType.URL,
                        source = FieldSource.OCR
                    )
                )
            }
        }

        val consolidatedFields = AddressConsolidator.consolidate(fields)
        ExtractionResult(currentDocType, consolidatedFields)
    }
}
