package com.myvault.app.domain.model

object PredefinedDocumentTypes {

    val CATEGORIES = listOf(
        "Bill",
        "Banking",
        "Receipt",
        "Certificate",
        "Identity",
        "Insurance",
        "Warranty",
        "Other"
    )

    val PREDEFINED_TYPES_BY_CATEGORY = mapOf(
        "Bill" to listOf(
            "Electricity Bill",
            "Gas Bill",
            "Water Bill",
            "Internet Bill",
            "Mobile Bill",
            "Telephone Bill",
            "Utility Bill",
            "Tax Bill"
        ),
        "Banking" to listOf(
            "Bank Statement",
            "Credit Card Statement",
            "Payment Receipt",
            "Invoice",
            "Cheque"
        ),
        "Receipt" to listOf(
            "Shopping Receipt",
            "Restaurant Receipt",
            "Medical Receipt",
            "Purchase Receipt"
        ),
        "Certificate" to listOf(
            "Birth Certificate",
            "Marriage Certificate",
            "Educational Certificate",
            "Degree Certificate",
            "Academic Transcript",
            "Experience Certificate",
            "Employment Letter"
        ),
        "Identity" to listOf(
            "CNIC",
            "Passport",
            "Driving License",
            "Identity Document"
        ),
        "Insurance" to listOf(
            "Insurance Policy",
            "Insurance Certificate"
        ),
        "Warranty" to listOf(
            "Warranty Card",
            "Warranty Certificate"
        ),
        "Other" to listOf(
            "Contract",
            "Agreement",
            "Property Document",
            "Vehicle Document",
            "Medical Document",
            "Prescription",
            "Other"
        )
    )

    val ALL_PREDEFINED_TYPES = PREDEFINED_TYPES_BY_CATEGORY.values.flatten()

    fun normalizeAndMatch(rawDocType: String?): MatchResult {
        if (rawDocType.isNullOrBlank()) {
            return MatchResult(docType = "UNKNOWN", category = "Other", isCustom = false)
        }

        val cleaned = rawDocType.trim().replace("_", " ").replace("-", " ")

        if (cleaned.equals("Custom", ignoreCase = true) || cleaned.equals("Custom Document Type", ignoreCase = true)) {
            return MatchResult(docType = "UNKNOWN", category = "Other", isCustom = true)
        }

        // Exact / Case-insensitive match against predefined types
        for (type in ALL_PREDEFINED_TYPES) {
            if (type.equals(cleaned, ignoreCase = true)) {
                return MatchResult(docType = type, category = getCategoryForType(type), isCustom = false)
            }
        }

        // Return as Custom document type preserving the actual custom value
        val customType = cleaned.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return MatchResult(docType = customType, category = getCategoryForType(customType), isCustom = true)
    }

    fun getCategoryForType(docType: String): String {
        for ((category, types) in PREDEFINED_TYPES_BY_CATEGORY) {
            if (types.any { it.equals(docType.trim(), ignoreCase = true) }) {
                return category
            }
        }
        return "Other"
    }

    data class MatchResult(
        val docType: String,
        val category: String,
        val isCustom: Boolean
    )
}
