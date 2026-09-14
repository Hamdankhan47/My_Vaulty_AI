package com.myvault.app.ui.home

import com.myvault.app.domain.model.Document

data class HomeUiState(
    val recentDocuments: List<Document> = emptyList(),
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val errorMessage: String? = null
)
