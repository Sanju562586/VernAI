package com.vernai.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.vernai.VernAiApplication
import com.vernai.ui.home.HomeScreen
import com.vernai.ui.home.HomeViewModel
import com.vernai.ui.settings.SettingsScreen
import com.vernai.ui.settings.SettingsViewModel

@Composable
fun VernAiNavigation() {
    val backStack = rememberNavBackStack(VernAiNavDestination.Home)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<VernAiNavDestination.Home> {
                val homeViewModel: HomeViewModel = viewModel {
                    HomeViewModel(VernAiApplication.instance.database)
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigate = { destination -> backStack.add(destination) }
                )
            }

            entry<VernAiNavDestination.Settings> {
                val settingsViewModel: SettingsViewModel = viewModel()
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { backStack.removeLastOrNull() }
                )
            }

            entry<VernAiNavDestination.SalesLedger> {
                PlaceholderFeatureScreen(
                    title = "Structured Sales Ledger",
                    description = "Feature implementation branch: feature/sales-log-extraction",
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<VernAiNavDestination.ComplaintDrafting> {
                PlaceholderFeatureScreen(
                    title = "Formal Complaint Drafting",
                    description = "Feature implementation branch: feature/complaint-letter-generation",
                    onBack = { backStack.removeLastOrNull() }
                )
            }

            entry<VernAiNavDestination.DocumentExplainer> {
                PlaceholderFeatureScreen(
                    title = "Document Explainer & OCR",
                    description = "Feature implementation branch: feature/document-explainer-ocr",
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderFeatureScreen(
    title: String,
    description: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
