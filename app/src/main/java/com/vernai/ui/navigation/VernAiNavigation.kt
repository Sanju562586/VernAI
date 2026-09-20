package com.vernai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.vernai.VernAiApplication
import com.vernai.core.preferences.UserPreferencesManager
import com.vernai.domain.repository.LocalComplaintRepository
import com.vernai.domain.repository.LocalDocumentRepository
import com.vernai.domain.repository.LocalSalesLogRepository
import com.vernai.ui.document.DocReaderScreen
import com.vernai.ui.document.DocReaderViewModel
import com.vernai.ui.home.HomeScreen
import com.vernai.ui.home.HomeViewModel
import com.vernai.ui.letter.LetterEditorScreen
import com.vernai.ui.letter.LetterEditorViewModel
import com.vernai.ui.onboarding.LanguageSelectionScreen
import com.vernai.ui.sales.SalesLogScreen
import com.vernai.ui.sales.SalesViewModel
import com.vernai.ui.settings.SettingsScreen
import com.vernai.ui.settings.SettingsViewModel
import com.vernai.ai.llm.benchmark.LlmBenchmarkRunner
import com.vernai.ui.voice.VoiceWorkspaceScreen
import com.vernai.ui.voice.VoiceWorkspaceViewModel

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface

@Composable
fun VernAiNavigation() {
    val app = VernAiApplication.instance
    val isConfigured = remember { UserPreferencesManager.isLanguageConfigured(app) }
    val initialDestination: VernAiNavDestination = if (isConfigured) {
        VernAiNavDestination.Home
    } else {
        VernAiNavDestination.LanguageSelection
    }
    val backStack = rememberNavBackStack(initialDestination)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<VernAiNavDestination.LanguageSelection> {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentLang = UserPreferencesManager.getPreferredLanguage(app)
                    LanguageSelectionScreen(
                        initialLanguage = currentLang,
                        onLanguageConfirmed = { selectedLang ->
                            UserPreferencesManager.setPreferredLanguage(app, selectedLang)
                            backStack.clear()
                            backStack.add(VernAiNavDestination.Home)
                        }
                    )
                }
            }

            entry<VernAiNavDestination.Home> {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val homeViewModel: HomeViewModel = viewModel {
                        HomeViewModel(
                            database = app.database,
                            context = app
                        )
                    }
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigate = { destination -> backStack.add(destination) }
                    )
                }
            }

            entry<VernAiNavDestination.VoiceWorkspace> {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val voiceViewModel: VoiceWorkspaceViewModel = viewModel {
                        VoiceWorkspaceViewModel(
                            asrEngine = app.asrEngine,
                            llmEngine = app.llmEngine,
                            dispatchers = app.dispatchers
                        )
                    }
                    VoiceWorkspaceScreen(
                        viewModel = voiceViewModel,
                        onNavigateBack = { backStack.removeLastOrNull() },
                        onNavigateToDestination = { destination -> backStack.add(destination) }
                    )
                }
            }

            entry<VernAiNavDestination.SalesLedger> {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val salesViewModel: SalesViewModel = viewModel {
                        SalesViewModel(
                            asrEngine = app.asrEngine,
                            salesLogRepository = LocalSalesLogRepository(app.database),
                            dispatchers = app.dispatchers
                        )
                    }
                    SalesLogScreen(
                        viewModel = salesViewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
            }

            entry<VernAiNavDestination.ComplaintDrafting> { destination ->
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val letterViewModel: LetterEditorViewModel = viewModel {
                        LetterEditorViewModel(
                            complaintRepository = LocalComplaintRepository(app.database),
                            llmEngine = app.llmEngine,
                            dispatchers = app.dispatchers,
                            initialTranscript = destination.initialTranscript
                        )
                    }
                    LetterEditorScreen(
                        viewModel = letterViewModel,
                        initialTranscript = destination.initialTranscript,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
            }

            entry<VernAiNavDestination.DocumentExplainer> {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val docViewModel: DocReaderViewModel = viewModel {
                        DocReaderViewModel(
                            repository = LocalDocumentRepository(app.database),
                            dispatchers = app.dispatchers
                        )
                    }
                    DocReaderScreen(
                        viewModel = docViewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
            }

            entry<VernAiNavDestination.Settings> {
                Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val settingsViewModel: SettingsViewModel = viewModel {
                        SettingsViewModel(
                            context = app,
                            benchmarkRunner = LlmBenchmarkRunner(
                                context = app,
                                engine = app.llmEngine,
                                dispatchers = app.dispatchers
                            )
                        )
                    }
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateBack = { backStack.removeLastOrNull() }
                    )
                }
            }
        }
    )
}
