package com.vernai.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

sealed interface VernAiNavDestination : NavKey {
    @Serializable
    data object Home : VernAiNavDestination

    @Serializable
    data object Settings : VernAiNavDestination

    @Serializable
    data object SalesLedger : VernAiNavDestination

    @Serializable
    data object ComplaintDrafting : VernAiNavDestination

    @Serializable
    data object DocumentExplainer : VernAiNavDestination
}
