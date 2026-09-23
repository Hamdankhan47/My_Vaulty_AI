package com.myvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.myvault.app.data.local.AppDatabase
import com.myvault.app.data.repository.DocumentRepositoryImpl
import com.myvault.app.domain.model.Document
import com.myvault.app.ui.detail.DocumentDetailScreen
import com.myvault.app.ui.home.HomeScreen
import com.myvault.app.ui.home.HomeViewModel
import com.myvault.app.ui.review.ReviewDocumentScreen
import com.myvault.app.ui.theme.MyVaultTheme
import com.myvault.app.ui.viewer.FileViewerScreen

sealed class Screen {
    object Home : Screen()
    object AllDocuments : Screen()
    data class Detail(val document: Document) : Screen()
    data class Review(val document: Document) : Screen()
    data class Viewer(val document: Document) : Screen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = DocumentRepositoryImpl(database, database.documentDao(), database.documentFieldDao())

        setContent {
            MyVaultTheme {
                val viewModelFactory = remember {
                    HomeViewModel.Factory(applicationContext, repository)
                }
                val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                BackHandler(enabled = currentScreen != Screen.Home) {
                    currentScreen = when (val screen = currentScreen) {
                        is Screen.Viewer -> Screen.Detail(screen.document)
                        is Screen.Review -> Screen.Detail(screen.document)
                        is Screen.Detail -> Screen.Home
                        is Screen.AllDocuments -> Screen.Home
                        is Screen.Home -> Screen.Home
                    }
                }

                when (val screen = currentScreen) {
                    is Screen.Home -> {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onDocumentClick = { document ->
                                currentScreen = Screen.Detail(document)
                            },
                            onImportSuccess = { newDocument ->
                                currentScreen = Screen.Detail(newDocument)
                            },
                            onViewAllClick = {
                                currentScreen = Screen.AllDocuments
                            }
                        )
                    }
                    is Screen.AllDocuments -> {
                        com.myvault.app.ui.home.AllDocumentsScreen(
                            viewModel = homeViewModel,
                            onBackClick = {
                                currentScreen = Screen.Home
                            },
                            onDocumentClick = { document ->
                                currentScreen = Screen.Detail(document)
                            },
                            onDeleteClick = { document ->
                                // Optional inline deletion or just let the detailed screen handle it. 
                                // Alternatively implemented via HomeViewModel
                                homeViewModel.deleteDocument(document, {}, {})
                            }
                        )
                    }
                    is Screen.Detail -> {
                        DocumentDetailScreen(
                            initialDocument = screen.document,
                            viewModel = homeViewModel,
                            onBackClick = {
                                currentScreen = Screen.Home
                            },
                            onEditFieldsClick = { document ->
                                currentScreen = Screen.Review(document)
                            },
                            onOpenFileClick = { document ->
                                currentScreen = Screen.Viewer(document)
                            }
                        )
                    }
                    is Screen.Review -> {
                        ReviewDocumentScreen(
                            document = screen.document,
                            viewModel = homeViewModel,
                            onBackClick = {
                                currentScreen = Screen.Detail(screen.document)
                            },
                            onSavedClick = {
                                currentScreen = Screen.Detail(screen.document)
                            }
                        )
                    }
                    is Screen.Viewer -> {
                        FileViewerScreen(
                            initialDocument = screen.document,
                            viewModel = homeViewModel,
                            onBackClick = {
                                currentScreen = Screen.Detail(screen.document)
                            }
                        )
                    }
                }
            }
        }
    }
}
