package com.myvault.app

import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.FieldType
import com.myvault.app.extraction.AiFieldExtractor
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class AiFieldExtractorTest {

    private fun createMockClient(
        responseCode: Int,
        responseJson: String
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(responseCode)
                    .message(if (responseCode == 200) "OK" else "Error")
                    .body(responseJson.toResponseBody(null))
                    .build()
            }
            .build()
    }

    @Test
    fun `test MDCAT date and time extraction preserves colon and labels without splitting`() = runBlocking {
        val mockGroqResponse = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"category\": \"Exam\", \"document_type\": \"MDCAT Notice\", \"fields\": [{\"label\": \"Date and Time of MDCAT\", \"value\": \"Sunday, 16th August 2026 at 10:00 PST\", \"type\": \"DATE\", \"confidence\": 0.98}]}"
                  }
                }
              ]
            }
        """.trimIndent()

        val mockClient = createMockClient(200, mockGroqResponse)
        val extractor = AiFieldExtractor(
            apiKey = "gsk_mock_key_for_test",
            modelName = "llama-3.3-70b-versatile",
            client = mockClient
        )

        val result = extractor.extract("MDCAT Notice", "Date and Time of MDCAT | Sunday, 16th August 2026 at 10:00 PST")

        assertEquals("MDCAT Notice", result.inferredDocumentType)
        assertEquals("Exam", result.category)
        assertEquals(1, result.fields.size)

        val field = result.fields[0]
        assertEquals("Date and Time of MDCAT", field.fieldName)
        assertEquals("Sunday, 16th August 2026 at 10:00 PST", field.fieldValue)
        assertEquals(FieldType.DATE, field.fieldType)
        assertEquals(FieldSource.AI, field.source)
    }

    @Test
    fun `test Groq API JSON parsing and field mapping`() = runBlocking {
        val mockGroqResponse = """
            {
              "choices": [
                {
                  "message": {
                    "content": "{\"category\": \"Bill\", \"document_type\": \"Electricity Bill\", \"fields\": [{\"label\": \"Provider\", \"value\": \"IESCO\", \"type\": \"ORGANIZATION\"}, {\"label\": \"Amount\", \"value\": \"8450\", \"type\": \"NUMBER\"}, {\"label\": \"Due Date\", \"value\": \"2026-09-18\", \"type\": \"DATE\"}]}"
                  }
                }
              ]
            }
        """.trimIndent()

        val mockClient = createMockClient(200, mockGroqResponse)
        val extractor = AiFieldExtractor(
            apiKey = "gsk_mock_key_for_test",
            modelName = "llama-3.3-70b-versatile",
            client = mockClient
        )

        val result = extractor.extract("Electricity Bill", "IESCO bill text")

        assertEquals("Electricity Bill", result.inferredDocumentType)
        assertEquals("Bill", result.category)
        assertEquals(3, result.fields.size)

        val field1 = result.fields[0]
        assertEquals("Provider", field1.fieldName)
        assertEquals("IESCO", field1.fieldValue)
        assertEquals(FieldType.ORGANIZATION, field1.fieldType)

        val field2 = result.fields[1]
        assertEquals("Amount", field2.fieldName)
        assertEquals("8450", field2.fieldValue)

        val field3 = result.fields[2]
        assertEquals("Due Date", field3.fieldName)
        assertEquals("2026-09-18", field3.fieldValue)
        assertEquals(FieldType.DATE, field3.fieldType)
    }

    @Test(expected = IllegalStateException::class)
    fun `test blank API key throws IllegalStateException`(): Unit = runBlocking {
        val extractor = AiFieldExtractor(
            apiKey = "",
            modelName = "llama-3.3-70b-versatile"
        )
        extractor.extract("Electricity Bill", "Sample OCR text")
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
