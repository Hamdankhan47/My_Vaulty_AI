package com.myvault.app.extraction

import android.util.Log
import com.myvault.app.data.remote.ExtractionRequestDto
import com.myvault.app.data.remote.MyVaultApi
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.FieldType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiFieldExtractor(
    private val api: MyVaultApi
) : FieldExtractor {

    override suspend fun extract(
        documentType: String?,
        ocrText: String
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val currentDocType = if (documentType.isNullOrBlank()) "UNKNOWN" else documentType
        if (ocrText.isBlank()) {
            Log.i("MyVaultE2E", "OCR TEXT BLANK - SKIPPING AI REQUEST")
            return@withContext ExtractionResult(currentDocType, emptyList())
        }

        val reqType = if (currentDocType == "UNKNOWN") null else currentDocType
        Log.i("MyVaultE2E", "HTTP REQUEST START")
        val response = api.extractDocumentFields(
            ExtractionRequestDto(documentType = reqType, ocrText = ocrText)
        )

        if (!response.isSuccessful || response.body() == null) {
            val errorMsg = response.errorBody()?.string() ?: response.message()
            throw IllegalStateException("API error ${response.code()}: $errorMsg")
        }

        val body = response.body()!!
        Log.i("MyVaultE2E", "HTTP REQUEST SUCCESS, fields=${body.fields.size}")
        val domainFields = body.fields.map { dto ->
            DocumentField(
                fieldName = dto.name,
                fieldValue = dto.value,
                fieldType = parseFieldType(dto.type),
                source = FieldSource.AI
            )
        }

        ExtractionResult(
            inferredDocumentType = body.documentType.ifEmpty { currentDocType },
            fields = domainFields
        )
    }

    private fun parseFieldType(typeStr: String): FieldType {
        return try {
            FieldType.valueOf(typeStr.uppercase())
        } catch (_: Exception) {
            FieldType.TEXT
        }
    }
}
