package com.myvault.app.domain.repository

import com.myvault.app.domain.model.Document
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.ExtractionStatus
import com.myvault.app.domain.model.OcrStatus
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun getRecentDocuments(): Flow<List<Document>>
    fun getAllDocuments(): Flow<List<Document>>
    suspend fun getDocumentById(id: Long): Document?
    fun getDocumentByIdFlow(id: Long): Flow<Document?>
    suspend fun insertDocument(document: Document): Long
    suspend fun updateDocumentDisplayName(documentId: Long, displayName: String)
    suspend fun updateDocumentType(documentId: Long, documentType: String, category: String, source: String = "USER")
    suspend fun updateOcrResult(documentId: Long, status: OcrStatus, ocrText: String?)
    suspend fun updateOcrStatus(documentId: Long, status: OcrStatus)
    suspend fun updateExtractionStatus(documentId: Long, status: ExtractionStatus, error: String? = null)
    suspend fun deleteDocument(document: Document)

    fun getDocumentFieldsFlow(documentId: Long): Flow<List<DocumentField>>
    suspend fun getDocumentFields(documentId: Long): List<DocumentField>
    suspend fun saveDocumentFields(documentId: Long, fields: List<DocumentField>)
    suspend fun updateDocumentWithFields(document: Document, fields: List<DocumentField>)
}
