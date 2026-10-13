package com.myvault.app.extraction

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.FieldType

object ValueTypeResolver {

    private val phoneUtil: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }

    private val EMAIL_REGEX = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\$")
    private val URL_REGEX = Regex("^(?:https?://|www\\.)[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?\$", RegexOption.IGNORE_CASE)
    private val DATE_REGEX = Regex("\\b\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}\\b|\\b\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4}\\b|\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun|Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)?,?\\s*\\d{1,2}(?:st|nd|rd|th)?\\s+(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec|January|February|March|April|June|July|August|September|October|November|December)\\s+\\d{4}\\b", RegexOption.IGNORE_CASE)
    private val ADDRESS_KEYWORDS = setOf("house", "street", "sector", "road", "block", "flat", "plot", "address", "location", "po box", "post code", "zip", "city", "district", "province", "country", "islamabad", "lahore", "karachi", "rawalpindi", "pakistan")

    fun deriveFieldType(value: String, suggestedType: FieldType = FieldType.TEXT): FieldType {
        val trimmedValue = value.trim()
        if (trimmedValue.isBlank()) return FieldType.TEXT

        // 1. Check EMAIL: Value MUST match email format
        if (EMAIL_REGEX.matches(trimmedValue)) {
            return FieldType.EMAIL
        }

        // 2. Check URL: Value MUST match web URL format
        if (URL_REGEX.matches(trimmedValue)) {
            return FieldType.URL
        }

        // 3. Check DATE: Value matches date formats (evaluated BEFORE PHONE to avoid false phone matches on YYYY-MM-DD)
        if (DATE_REGEX.containsMatchIn(trimmedValue)) {
            return FieldType.DATE
        }

        // 4. Check PHONE: Use libphonenumber (default region PK) + extension support
        if (isPhoneNumber(trimmedValue)) {
            return FieldType.PHONE
        }

        // 5. Check ADDRESS: Value contains address location keywords
        val lowerValue = trimmedValue.lowercase()
        if (ADDRESS_KEYWORDS.any { lowerValue.contains(it) }) {
            return FieldType.ADDRESS
        }

        // 6. If suggestedType is PERSON_NAME/ORGANIZATION/CURRENCY/NUMBER/ADDRESS, keep suggestedType
        if (suggestedType in setOf(FieldType.ADDRESS, FieldType.PERSON_NAME, FieldType.ORGANIZATION, FieldType.CURRENCY, FieldType.NUMBER)) {
            return suggestedType
        }

        return FieldType.TEXT
    }

    private fun isPhoneNumber(value: String): Boolean {
        if (value.isBlank()) return false
        val clean = value.lowercase()
        val hasPhoneKeywords = clean.contains("ph") || clean.contains("tel") || clean.contains("phone") || clean.contains("mobile") || clean.contains("ext")
        val digitCount = value.count { it.isDigit() }
        if (digitCount < 5) return false

        return try {
            val proto = phoneUtil.parse(value, "PK")
            phoneUtil.isValidNumber(proto) || phoneUtil.isPossibleNumber(proto) || hasPhoneKeywords
        } catch (_: Exception) {
            hasPhoneKeywords
        }
    }

    fun deduplicateFields(fields: List<DocumentField>): List<DocumentField> {
        val seenNormalizedValues = mutableSetOf<String>()
        val result = mutableListOf<DocumentField>()

        for (field in fields) {
            val normValue = field.fieldValue.trim().lowercase().replace("\\s+".toRegex(), " ")
            if (normValue.isBlank()) continue

            if (seenNormalizedValues.contains(normValue)) {
                continue
            }
            seenNormalizedValues.add(normValue)
            result.add(field)
        }
        return result
    }
}
