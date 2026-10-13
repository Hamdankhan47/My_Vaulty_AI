package com.myvault.app.extraction

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.myvault.app.BuildConfig
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.FieldType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class ExtractedFieldDto(
    val label: String? = null,
    val name: String? = null,
    val value: String = "",
    val type: String = "TEXT",
    val confidence: Double = 1.0
)

@Serializable
data class ExtractionResponseDto(
    val category: String = "Other",
    @SerialName("document_type") val documentType: String = "UNKNOWN",
    val fields: List<ExtractedFieldDto> = emptyList()
)

class AiFieldExtractor(
    private val apiKey: String = BuildConfig.GROQ_API_KEY,
    private val modelName: String = BuildConfig.GROQ_MODEL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)
            var tryCount = 0
            val maxRetries = 3
            while (!response.isSuccessful && tryCount < maxRetries && (response.code == 429 || response.code >= 500)) {
                tryCount++
                response.close()
                try {
                    Thread.sleep(1000L * tryCount)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                response = chain.proceed(request)
            }
            response
        }
        .build()
) : FieldExtractor {

    private val gson = Gson()
    private val jsonSerializer = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        private const val TAG = "AiFieldExtractor"

        private val PLACEHOLDER_STRINGS = setOf(
            "N/A", "UNKNOWN", "NOT SPECIFIED", "NONE", "NULL", "NOT AVAILABLE", "N / A"
        )

        private const val SYSTEM_PROMPT = """You are an expert AI document parsing assistant for MyVault.
Your task is to analyze raw OCR text and extract comprehensive, accurate, document-aware structured fields.

=== BASE EXTRACTION RULES ===
1. FACTUAL GROUNDING: Extract ONLY information explicitly supported by the OCR text. Never guess, infer missing values, or invent information.
2. OMIT ABSENT FIELDS: If a field does not appear in the OCR text, DO NOT include it. Never output "N/A", "Unknown", "None", or placeholder strings.
3. PRESERVE VALUES & TIMESTAMPS: Extract label and value exactly as written. Never split values on colons or truncating timestamps (e.g. "10:00 PST" must remain "10:00 PST"). Do NOT title-case or alter casing.
4. PRESERVE IDENTIFIERS: Numbers like Consumer Number, Reference Number, Meter Number, Account Number, Invoice Number, CNIC, and Roll Number MUST be assigned type "TEXT" (never "PHONE" or "NUMBER").
5. FIELD TYPE ASSIGNMENT:
   - "TEXT": Identifiers, status strings, plan names, descriptions, or general text.
   - "NUMBER": Counts or meter reading units.
   - "DATE": Dates and timestamps.
   - "CURRENCY": Monetary values.
   - "PHONE": Telephone/contact numbers with extensions.
   - "EMAIL": Email addresses.
   - "URL": Website links/URLs.
   - "ADDRESS": Consolidated physical addresses.
   - "PERSON_NAME": Customer or contact name.
   - "ORGANIZATION": Provider name, company, merchant, or bank.

=== OUTPUT FORMAT ===
Return ONLY valid JSON matching this exact structure:
{
  "category": "Bill",
  "document_type": "Electricity Bill",
  "fields": [
    {"label": "Date and Time of MDCAT", "value": "Sunday, 16th August 2026 at 10:00 PST", "type": "DATE", "confidence": 0.98},
    {"label": "Phone Number", "value": "Ph 051-9190000 Ext: 302", "type": "PHONE", "confidence": 0.95}
  ]
}"""
    }

    override suspend fun extract(
        documentType: String?,
        ocrText: String
    ): ExtractionResult = withContext(Dispatchers.IO) {
        val currentDocType = if (documentType.isNullOrBlank()) "UNKNOWN" else documentType
        if (ocrText.isBlank()) {
            Log.i(TAG, "OCR TEXT BLANK - SKIPPING AI REQUEST")
            return@withContext ExtractionResult(currentDocType, emptyList())
        }

        if (apiKey.isBlank()) {
            throw IllegalStateException("AI API key is not configured in local.properties")
        }

        val userContext = "Document Type Context: $currentDocType\n\nRaw OCR Text:\n$ocrText"
        Log.i(TAG, "STARTING DIRECT AI REQUEST")

        val rawResponseJson = try {
            executeAiRequest(userContext)
        } catch (e: Exception) {
            Log.w(TAG, "First AI API call failed: ${e.message}. Retrying once...")
            executeAiRequest(userContext)
        }

        try {
            parseAndValidateResponse(rawResponseJson, currentDocType)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON output on first attempt (${e.message}). Retrying LLM extraction once...")
            val retryRawResponseJson = executeAiRequest(userContext)
            parseAndValidateResponse(retryRawResponseJson, currentDocType)
        }
    }

    private fun executeAiRequest(userContext: String): String {
        return if (apiKey.startsWith("sk-or-")) {
            callOpenRouterApi(userContext)
        } else if (apiKey.startsWith("gsk_")) {
            callGroqApi(userContext)
        } else {
            callOpenRouterApi(userContext)
        }
    }

    private fun callOpenRouterApi(userContext: String): String {
        var openRouterModel = modelName.trim()
        if (openRouterModel.isBlank() || openRouterModel.contains("groq")) {
            openRouterModel = "openai/gpt-4o-mini"
        }

        return try {
            executeOpenRouterRequest(openRouterModel, userContext)
        } catch (e: IOException) {
            if ((e.message?.contains("402") == true || e.message?.contains("404") == true) && openRouterModel != "google/gemini-2.0-flash-lite-001") {
                Log.w(TAG, "OpenRouter Model '$openRouterModel' returned error. Falling back to 'google/gemini-2.0-flash-lite-001'")
                executeOpenRouterRequest("google/gemini-2.0-flash-lite-001", userContext)
            } else {
                throw e
            }
        }
    }

    private fun executeOpenRouterRequest(model: String, userContext: String): String {
        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", SYSTEM_PROMPT)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userContext)
                })
            })
            add("response_format", JsonObject().apply {
                addProperty("type", "json_object")
            })
            addProperty("temperature", 0.1)
            addProperty("max_tokens", 1200)
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("HTTP-Referer", "https://myvault.app")
            .addHeader("X-Title", "MyVault AI")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    429 -> "AI API rate limit reached. Please wait a few seconds and try again."
                    401 -> "Invalid OpenRouter API key."
                    402 -> "OpenRouter credit limit reached for model '$model': $bodyString"
                    404 -> "OpenRouter API error 404 for model '$model': $bodyString"
                    else -> "OpenRouter API error ${response.code}: $bodyString"
                }
                Log.e(TAG, "OpenRouter HTTP Error ${response.code}: $bodyString")
                throw IOException(errorMsg)
            }

            val jsonResponse = gson.fromJson(bodyString, JsonObject::class.java)
            val choices = jsonResponse.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                throw IOException("Empty choices response from OpenRouter API")
            }
            return choices.get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
        }
    }

    private fun callGroqApi(userContext: String): String {
        var groqModel = modelName.trim()
        if (groqModel.startsWith("groq/")) {
            groqModel = groqModel.removePrefix("groq/").trim()
        }
        val invalidGroqModels = setOf("compound", "groq/compound", "openai/gpt-oss-120b", "gpt-oss-120b")
        if (groqModel.isBlank() || invalidGroqModels.contains(groqModel.lowercase())) {
            groqModel = "llama-3.3-70b-versatile"
        }

        return try {
            executeGroqRequest(groqModel, userContext)
        } catch (e: IOException) {
            if (e.message?.contains("404") == true) {
                if (groqModel != "llama-3.3-70b-versatile") {
                    Log.w(TAG, "Groq Model '$groqModel' returned 404. Falling back to 'llama-3.3-70b-versatile'")
                    executeGroqRequest("llama-3.3-70b-versatile", userContext)
                } else {
                    Log.w(TAG, "Groq Model '$groqModel' returned 404. Falling back to 'llama-3.1-8b-instant'")
                    executeGroqRequest("llama-3.1-8b-instant", userContext)
                }
            } else {
                throw e
            }
        }
    }

    private fun executeGroqRequest(model: String, userContext: String): String {
        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", SYSTEM_PROMPT)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userContext)
                })
            })
            add("response_format", JsonObject().apply {
                addProperty("type", "json_object")
            })
            addProperty("temperature", 0.1)
        }

        val request = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val errorMsg = when (response.code) {
                    429 -> "AI API rate limit reached. Please wait a few seconds and try again."
                    404 -> "Groq API error 404 for model '$model': $bodyString"
                    else -> "Groq API error ${response.code}: $bodyString"
                }
                Log.e(TAG, "Groq HTTP Error ${response.code}: $bodyString")
                throw IOException(errorMsg)
            }

            val jsonResponse = gson.fromJson(bodyString, JsonObject::class.java)
            val choices = jsonResponse.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                throw IOException("Empty choices response from Groq API")
            }
            return choices.get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
        }
    }

    private fun parseAndValidateResponse(rawContent: String, defaultDocType: String): ExtractionResult {
        var cleanJson = rawContent.trim()
        if (cleanJson.startsWith("```json")) cleanJson = cleanJson.substring(7)
        if (cleanJson.startsWith("```")) cleanJson = cleanJson.substring(3)
        if (cleanJson.endsWith("```")) cleanJson = cleanJson.substring(0, cleanJson.length - 3)
        cleanJson = cleanJson.trim()

        val firstBrace = cleanJson.indexOf('{')
        val lastBrace = cleanJson.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace > firstBrace) {
            cleanJson = cleanJson.substring(firstBrace, lastBrace + 1)
        }

        val dto = jsonSerializer.decodeFromString<ExtractionResponseDto>(cleanJson)

        val validFields = mutableListOf<DocumentField>()

        for (fieldDto in dto.fields) {
            val label = (fieldDto.label ?: fieldDto.name ?: "").trim()
            val value = fieldDto.value.trim()

            if (label.isBlank() || value.isBlank() || PLACEHOLDER_STRINGS.contains(value.uppercase())) {
                continue
            }

            val suggestedType = try {
                FieldType.valueOf(fieldDto.type.uppercase().trim())
            } catch (_: Exception) {
                FieldType.TEXT
            }

            // Derive type from VALUE, not label!
            val derivedType = ValueTypeResolver.deriveFieldType(value = value, suggestedType = suggestedType)

            validFields.add(
                DocumentField(
                    fieldName = label,
                    fieldValue = value, // PRESERVE EXACT VALUE (no title casing or mutation)
                    fieldType = derivedType,
                    source = FieldSource.AI,
                    confidence = fieldDto.confidence
                )
            )
        }

        val category = dto.category.ifBlank { "Other" }
        val docType = dto.documentType.ifBlank { defaultDocType }

        val consolidated = AddressConsolidator.consolidate(validFields)
        val deduplicated = ValueTypeResolver.deduplicateFields(consolidated)

        return ExtractionResult(
            inferredDocumentType = if (docType.isBlank()) defaultDocType else docType,
            fields = deduplicated,
            category = category
        )
    }
}
