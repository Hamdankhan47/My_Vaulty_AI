package com.myvault.app.extraction

import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.FieldType

object AddressConsolidator {

    private val ADDRESS_KEYWORDS = setOf(
        "address", "location", "street", "city", "area", "sector",
        "district", "province", "state", "country", "postal code", "zip"
    )

    fun consolidate(fields: List<DocumentField>): List<DocumentField> {
        if (fields.isEmpty()) return fields

        val addressCandidates = mutableListOf<DocumentField>()
        val nonAddressFields = mutableListOf<DocumentField>()

        for (field in fields) {
            val label = field.fieldName.lowercase().trim()
            val isAddressType = field.fieldType == FieldType.ADDRESS
            val isAddressKeyword = ADDRESS_KEYWORDS.any { label.contains(it) }

            if (isAddressType || isAddressKeyword) {
                addressCandidates.add(field)
            } else {
                nonAddressFields.add(field)
            }
        }

        if (addressCandidates.isEmpty()) {
            return fields
        }

        // Check if there are distinct address groups (e.g., "Billing Address" vs "Office Address")
        val groupedBySpecificLabel = addressCandidates.groupBy { field ->
            val label = field.fieldName.lowercase().trim()
            when {
                label.contains("billing") -> "Billing Address"
                label.contains("office") -> "Office Address"
                label.contains("shipping") -> "Shipping Address"
                label.contains("residential") || label.contains("home") -> "Residential Address"
                else -> "Main Address"
            }
        }

        val consolidatedResult = mutableListOf<DocumentField>()
        consolidatedResult.addAll(nonAddressFields)

        for ((groupName, groupFields) in groupedBySpecificLabel) {
            // Deduplicate exact or substring values
            val uniqueValues = mutableListOf<String>()
            for (f in groupFields) {
                val valTrimmed = f.fieldValue.trim()
                if (valTrimmed.isBlank()) continue

                // Check if this value is already contained in an existing value
                val isAlreadyIncluded = uniqueValues.any { existing ->
                    existing.lowercase().contains(valTrimmed.lowercase())
                }
                if (!isAlreadyIncluded) {
                    // Filter out shorter strings that are substrings of this new value
                    uniqueValues.removeAll { existing ->
                        valTrimmed.lowercase().contains(existing.lowercase())
                    }
                    uniqueValues.add(valTrimmed)
                }
            }

            if (uniqueValues.isNotEmpty()) {
                val consolidatedValue = uniqueValues.joinToString(", ")
                val source = groupFields.firstOrNull()?.source ?: addressCandidates.first().source
                val fieldName = if (groupName == "Main Address") {
                    groupFields.firstOrNull { it.fieldName.lowercase().contains("address") }?.fieldName ?: "Address"
                } else {
                    groupName
                }

                consolidatedResult.add(
                    DocumentField(
                        fieldName = fieldName,
                        fieldValue = consolidatedValue,
                        fieldType = FieldType.ADDRESS,
                        source = source
                    )
                )
            }
        }

        return consolidatedResult
    }
}
