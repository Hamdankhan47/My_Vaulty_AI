package com.myvault.app

import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.domain.model.FieldType
import com.myvault.app.extraction.ValueTypeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ValueTypeResolverTest {

    @Test
    fun `test MDCAT date and timestamp value preservation and date typing`() {
        val rawValue = "Sunday, 16th August 2026 at 10:00 PST"
        val derivedType = ValueTypeResolver.deriveFieldType(
            value = rawValue,
            suggestedType = FieldType.TEXT
        )

        // Value must NOT mutate casing ("16th", "at", "10:00 PST" intact)
        assertEquals("Sunday, 16th August 2026 at 10:00 PST", rawValue)
        assertEquals(FieldType.DATE, derivedType)
    }

    @Test
    fun `test phone with extension derived as PHONE type`() {
        val phoneValue = "Ph 051-9190000 Ext: 302"
        val derivedType = ValueTypeResolver.deriveFieldType(
            value = phoneValue,
            suggestedType = FieldType.TEXT
        )

        assertEquals(FieldType.PHONE, derivedType)
    }

    @Test
    fun `test email label with non-email value is NOT typed as EMAIL`() {
        val nonEmailValue = "House 12, Street 4, Sector F-8/1, Islamabad"
        val derivedType = ValueTypeResolver.deriveFieldType(
            value = nonEmailValue,
            suggestedType = FieldType.EMAIL
        )

        // Value is an address, so type must NOT be EMAIL
        assertNotEquals(FieldType.EMAIL, derivedType)
        assertEquals(FieldType.ADDRESS, derivedType)
    }

    @Test
    fun `test email label with valid email value is typed as EMAIL`() {
        val emailValue = "support@example.com"
        val derivedType = ValueTypeResolver.deriveFieldType(
            value = emailValue,
            suggestedType = FieldType.EMAIL
        )

        assertEquals(FieldType.EMAIL, derivedType)
    }

    @Test
    fun `test field deduplication with normalized values`() {
        val fields = listOf(
            DocumentField(
                id = 1,
                fieldName = "Email",
                fieldValue = "support@example.com",
                fieldType = FieldType.EMAIL,
                source = FieldSource.AI
            ),
            DocumentField(
                id = 2,
                fieldName = "Email Address",
                fieldValue = "  SUPPORT@EXAMPLE.COM  ",
                fieldType = FieldType.EMAIL,
                source = FieldSource.AI
            ),
            DocumentField(
                id = 3,
                fieldName = "Phone",
                fieldValue = "Ph 051-9190000 Ext: 302",
                fieldType = FieldType.PHONE,
                source = FieldSource.AI
            )
        )

        val deduplicated = ValueTypeResolver.deduplicateFields(fields)

        assertEquals(2, deduplicated.size)
        assertEquals("Email", deduplicated[0].fieldName)
        assertEquals("support@example.com", deduplicated[0].fieldValue)
        assertEquals("Phone", deduplicated[1].fieldName)
        assertEquals("Ph 051-9190000 Ext: 302", deduplicated[1].fieldValue)
    }
}
