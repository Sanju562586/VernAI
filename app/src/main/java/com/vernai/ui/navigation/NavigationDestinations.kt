package com.vernai.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface VernAiNavDestination : NavKey {
    @Serializable
    data object Home : VernAiNavDestination

    @Serializable
    data object VoiceWorkspace : VernAiNavDestination

    @Serializable
    data object Settings : VernAiNavDestination

    @Serializable
    data class SalesLedger(val initialTranscript: String? = null) : VernAiNavDestination

    @Serializable
    data class ComplaintDrafting(val initialTranscript: String? = null) : VernAiNavDestination

    @Serializable
    data object DocumentExplainer : VernAiNavDestination

    @Serializable
    data object LanguageSelection : VernAiNavDestination
}
