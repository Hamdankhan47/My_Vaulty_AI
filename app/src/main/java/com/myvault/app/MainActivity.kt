package com.myvault.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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

sealed class Screen {
    object Home : Screen()
    data class Detail(val document: Document) : Screen()
    data class Review(val document: Document) : Screen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = DocumentRepositoryImpl(database.documentDao(), database.documentFieldDao())

        setContent {
            MyVaultTheme {
                val viewModelFactory = remember {
                    HomeViewModel.Factory(applicationContext, repository)
                }
                val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                when (val screen = currentScreen) {
                    is Screen.Home -> {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onDocumentClick = { document ->
                                currentScreen = Screen.Detail(document)
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
                }
            }
        }
    }
}
