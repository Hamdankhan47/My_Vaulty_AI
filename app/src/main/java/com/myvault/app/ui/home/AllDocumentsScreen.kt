package com.myvault.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myvault.app.domain.model.Document
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllDocumentsScreen(
    viewModel: HomeViewModel,
    onBackClick: () -> Unit,
    onDocumentClick: (Document) -> Unit,
    onDeleteClick: (Document) -> Unit
) {
    // Collect all documents instead of just recent 20
    val allDocuments by viewModel.getAllDocumentsFlow().collectAsState(initial = emptyList())
    val isGridView by viewModel.isGridView.collectAsState()

    var selectedDocumentIds by remember { mutableStateOf(emptySet<Long>()) }
    var showMultiDeleteConfirmationDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            if (selectedDocumentIds.isNotEmpty()) {
                val allIds = allDocuments.map { it.id }.toSet()
                val isAllSelected = allIds.isNotEmpty() && selectedDocumentIds.containsAll(allIds)

                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedDocumentIds.size} Selected",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { selectedDocumentIds = emptySet() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Selection"
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                selectedDocumentIds = if (isAllSelected) emptySet() else allIds
                            }
                        ) {
                            Text(
                                text = if (isAllSelected) "Deselect All" else "Select All",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        IconButton(
                            onClick = { showMultiDeleteConfirmationDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = { Text("All Documents", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (allDocuments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No documents found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(allDocuments, key = { it.id }) { document ->
                            val isSelected = document.id in selectedDocumentIds
                            val isSelectionMode = selectedDocumentIds.isNotEmpty()

                            DocumentGridItem(
                                document = document,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedDocumentIds = if (isSelected) selectedDocumentIds - document.id else selectedDocumentIds + document.id
                                    } else {
                                        onDocumentClick(document)
                                    }
                                },
                                onDeleteClick = { onDeleteClick(document) },
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onLongClick = {
                                    selectedDocumentIds = if (isSelected) selectedDocumentIds - document.id else selectedDocumentIds + document.id
                                }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        items(allDocuments, key = { it.id }) { document ->
                            val isSelected = document.id in selectedDocumentIds
                            val isSelectionMode = selectedDocumentIds.isNotEmpty()

                            DocumentListItem(
                                document = document,
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedDocumentIds = if (isSelected) selectedDocumentIds - document.id else selectedDocumentIds + document.id
                                    } else {
                                        onDocumentClick(document)
                                    }
                                },
                                onDeleteClick = { onDeleteClick(document) },
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onLongClick = {
                                    selectedDocumentIds = if (isSelected) selectedDocumentIds - document.id else selectedDocumentIds + document.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMultiDeleteConfirmationDialog && selectedDocumentIds.isNotEmpty()) {
        val count = selectedDocumentIds.size
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirmationDialog = false },
            title = { Text(text = "Delete $count selected document(s)?") },
            text = {
                Text(text = "Are you sure you want to permanently delete $count selected document(s)? This action cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val docsToDelete = allDocuments.filter { it.id in selectedDocumentIds }
                        showMultiDeleteConfirmationDialog = false
                        val deleteCount = docsToDelete.size
                        viewModel.deleteDocuments(
                            documents = docsToDelete,
                            onSuccess = {
                                selectedDocumentIds = emptySet()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Deleted $deleteCount document(s)")
                                }
                            },
                            onError = { errorMsg ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(errorMsg)
                                }
                            }
                        )
                    }
                ) {
                    Text(
                        text = "Delete ($count)",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteConfirmationDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}
