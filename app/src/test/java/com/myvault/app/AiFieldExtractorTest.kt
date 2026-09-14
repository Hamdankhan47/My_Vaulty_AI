package com.myvault.app

import com.myvault.app.data.remote.ExtractedFieldDto
import com.myvault.app.data.remote.ExtractionRequestDto
import com.myvault.app.data.remote.ExtractionResponseDto
import com.myvault.app.data.remote.MyVaultApi
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.FieldType
import com.myvault.app.extraction.AiFieldExtractor
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class AiFieldExtractorTest {

    private class FakeMyVaultApi(
        private val responseToReturn: Response<ExtractionResponseDto>
    ) : MyVaultApi {
        var lastRequest: ExtractionRequestDto? = null

        override suspend fun extractDocumentFields(request: ExtractionRequestDto): Response<ExtractionResponseDto> {
            lastRequest = request
            return responseToReturn
        }
    }

    @Test
    fun `test API DTO parsing and field mapping`() = runBlocking {
        val dtoResponse = ExtractionResponseDto(
            documentType = "ELECTRICITY_BILL",
            fields = listOf(
                ExtractedFieldDto("Provider", "IESCO", "TEXT"),
                ExtractedFieldDto("Amount", "8450", "NUMBER"),
                ExtractedFieldDto("Due Date", "2026-09-18", "DATE")
            )
        )
        val api = FakeMyVaultApi(Response.success(dtoResponse))
        val extractor = AiFieldExtractor(api)

        val result = extractor.extract("ELECTRICITY_BILL", "IESCO bill text")

        assertEquals("ELECTRICITY_BILL", result.inferredDocumentType)
        assertEquals(3, result.fields.size)

        val field1 = result.fields[0]
        assertEquals("Provider", field1.fieldName)
        assertEquals("IESCO", field1.fieldValue)
        assertEquals(FieldType.TEXT, field1.fieldType)
        assertEquals(FieldSource.AI, field1.source)

        val field2 = result.fields[1]
        assertEquals("Amount", field2.fieldName)
        assertEquals("8450", field2.fieldValue)
        assertEquals(FieldType.NUMBER, field2.fieldType)
        assertEquals(FieldSource.AI, field2.source)

        val field3 = result.fields[2]
        assertEquals("Due Date", field3.fieldName)
        assertEquals("2026-09-18", field3.fieldValue)
        assertEquals(FieldType.DATE, field3.fieldType)
        assertEquals(FieldSource.AI, field3.source)
    }

    @Test(expected = IllegalStateException::class)
    fun `test network error throws exception for fallback handling`(): Unit = runBlocking {
        val api = FakeMyVaultApi(Response.error(502, "Bad Gateway".toResponseBody(null)))
        val extractor = AiFieldExtractor(api)

        extractor.extract(null, "Sample OCR text")
    }

    @Test
    fun `test USER field protection logic`() {
        val existingFields = listOf(
            DocumentField(id = 1, fieldName = "Amount", fieldValue = "8450", source = FieldSource.USER),
            DocumentField(id = 2, fieldName = "Provider", fieldValue = "IESCO", source = FieldSource.OCR)
        )

        val newAiFields = listOf(
            DocumentField(id = 0, fieldName = "Amount", fieldValue = "9000", source = FieldSource.AI),
            DocumentField(id = 0, fieldName = "Due Date", fieldValue = "2026-09-18", source = FieldSource.AI)
        )

        val userFields = existingFields.filter { it.source == FieldSource.USER }
        val userFieldNames = userFields.map { it.fieldName.lowercase().trim() }.toSet()

        val merged = mutableListOf<DocumentField>()
        merged.addAll(userFields)

        for (newField in newAiFields) {
            if (!userFieldNames.contains(newField.fieldName.lowercase().trim())) {
                merged.add(newField)
            }
        }

        // Amount value must remain 8450 with USER source
        assertEquals(2, merged.size)
        val amountField = merged.find { it.fieldName == "Amount" }!!
        assertEquals("8450", amountField.fieldValue)
        assertEquals(FieldSource.USER, amountField.source)

        val dueDateField = merged.find { it.fieldName == "Due Date" }!!
        assertEquals("2026-09-18", dueDateField.fieldValue)
        assertEquals(FieldSource.AI, dueDateField.source)
    }
}
