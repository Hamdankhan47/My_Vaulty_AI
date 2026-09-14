package com.myvault.app.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.myvault.app.domain.model.Document
import com.myvault.app.domain.model.DocumentField
import com.myvault.app.domain.model.FieldSource
import com.myvault.app.ui.home.HomeViewModel

val DOCUMENT_TYPE_OPTIONS = listOf(
    "UNKNOWN",
    "ELECTRICITY_BILL",
    "GAS_BILL",
    "INTERNET_BILL",
    "BANK_STATEMENT",
    "WARRANTY_CARD",
    "CERTIFICATE",
    "RECEIPT"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewDocumentScreen(
    document: Document,
    viewModel: HomeViewModel,
    onBackClick: () -> Unit,
    onSavedClick: () -> Unit
) {
    val initialFields by viewModel.getDocumentFieldsFlow(document.id).collectAsState(initial = emptyList())

    var docTitle by remember { mutableStateOf(document.title) }
    var docCategory by remember { mutableStateOf(document.category) }
    var selectedDocType by remember { mutableStateOf(document.documentType) }
    var docTypeExpanded by remember { mutableStateOf(false) }

    var showAddFieldDialog by remember { mutableStateOf(false) }

    val fieldsList = remember { mutableStateListOf<DocumentField>() }

    // Populate initial fields when loaded
    LaunchedEffect(initialFields) {
        if (fieldsList.isEmpty() && initialFields.isNotEmpty()) {
            fieldsList.clear()
            fieldsList.addAll(initialFields)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Review & Edit Fields") },
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
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Document Metadata Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Document Properties",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = docTitle,
                        onValueChange = { docTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = docCategory,
                        onValueChange = { docCategory = it },
                        label = { Text("Category") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ExposedDropdownMenuBox(
                        expanded = docTypeExpanded,
                        onExpandedChange = { docTypeExpanded = !docTypeExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedDocType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Document Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = docTypeExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = docTypeExpanded,
                            onDismissRequest = { docTypeExpanded = false }
                        ) {
                            DOCUMENT_TYPE_OPTIONS.forEach { typeOption ->
                                DropdownMenuItem(
                                    text = { Text(typeOption) },
                                    onClick = {
                                        selectedDocType = typeOption
                                        docTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Structured Fields Section Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Structured Fields",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                OutlinedButton(onClick = { showAddFieldDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Add Field")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (fieldsList.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No structured fields extracted yet. Tap '+ Add Field' to add custom fields.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                fieldsList.forEachIndexed { index, field ->
                    FieldEditCard(
                        field = field,
                        onFieldChange = { updatedField ->
                            fieldsList[index] = updatedField.copy(source = FieldSource.USER)
                        },
                        onDeleteClick = {
                            fieldsList.removeAt(index)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val updatedDoc = document.copy(
                        title = docTitle.trim().ifEmpty { document.title },
                        category = docCategory.trim().ifEmpty { document.category },
                        documentType = selectedDocType
                    )
                    viewModel.saveDocumentAndFields(updatedDoc, fieldsList.toList()) {
                        onSavedClick()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save Changes")
            }
        }
    }

    if (showAddFieldDialog) {
        AddFieldDialog(
            onDismissRequest = { showAddFieldDialog = false },
            onAddField = { name, value, type ->
                fieldsList.add(
                    DocumentField(
                        documentId = document.id,
                        fieldName = name,
                        fieldValue = value,
                        fieldType = type,
                        source = FieldSource.USER
                    )
                )
            }
        )
    }
}

@Composable
private fun FieldEditCard(
    field: DocumentField,
    onFieldChange: (DocumentField) -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source: ${field.source.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Field",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(
                value = field.fieldName,
                onValueChange = { onFieldChange(field.copy(fieldName = it)) },
                label = { Text("Field Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = field.fieldValue,
                onValueChange = { onFieldChange(field.copy(fieldValue = it)) },
                label = { Text("Field Value") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
