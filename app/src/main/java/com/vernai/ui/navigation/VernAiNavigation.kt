package com.vernai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.vernai.VernAiApplication
import com.vernai.domain.repository.LocalComplaintRepository
import com.vernai.domain.repository.LocalDocumentRepository
import com.vernai.domain.repository.LocalSalesLogRepository
import com.vernai.ui.document.DocReaderScreen
import com.vernai.ui.document.DocReaderViewModel
import com.vernai.ui.home.HomeScreen
import com.vernai.ui.home.HomeViewModel
import com.vernai.ui.letter.LetterEditorScreen
import com.vernai.ui.letter.LetterEditorViewModel
import com.vernai.ui.sales.SalesLogScreen
import com.vernai.ui.sales.SalesViewModel
import com.vernai.ui.settings.SettingsScreen
import com.vernai.ui.settings.SettingsViewModel
import com.vernai.ai.llm.benchmark.LlmBenchmarkRunner
import com.vernai.ui.voice.VoiceWorkspaceScreen
import com.vernai.ui.voice.VoiceWorkspaceViewModel

@Composable
fun VernAiNavigation() {
    val backStack = rememberNavBackStack(VernAiNavDestination.Home)
    val app = VernAiApplication.instance

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<VernAiNavDestination.Home> {
                val homeViewModel: HomeViewModel = viewModel {
                    HomeViewModel(app.database)
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigate = { destination -> backStack.add(destination) }
                )
            }

            entry<VernAiNavDestination.VoiceWorkspace> {
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

            entry<VernAiNavDestination.SalesLedger> {
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

            entry<VernAiNavDestination.ComplaintDrafting> { destination ->
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

            entry<VernAiNavDestination.DocumentExplainer> {
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

            entry<VernAiNavDestination.Settings> {
                val settingsViewModel: SettingsViewModel = viewModel {
                    SettingsViewModel(
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
    )
}
