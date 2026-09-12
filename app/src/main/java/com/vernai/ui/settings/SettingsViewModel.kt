package com.vernai.ui.settings

import com.vernai.ui.common.MviViewModel

class SettingsViewModel : MviViewModel<SettingsUiState, SettingsUiIntent, SettingsUiSideEffect>(SettingsUiState()) {

    override fun handleIntent(intent: SettingsUiIntent) {
        when (intent) {
            is SettingsUiIntent.ChangeLanguage -> {
                setState { copy(selectedLanguage = intent.language) }
                sendSideEffect(SettingsUiSideEffect.ShowToast("భాష మార్చబడింది (Language Updated to ${intent.language.nativeName})"))
            }
            is SettingsUiIntent.UpdateThreadCount -> {
                setState { copy(threadCount = intent.threads.coerceIn(1, 8)) }
            }
            is SettingsUiIntent.ToggleVulkan -> {
                setState { copy(useVulkanAcceleration = intent.enabled) }
            }
            is SettingsUiIntent.ClearDatabaseCache -> {
                sendSideEffect(SettingsUiSideEffect.ShowToast("స్థానిక కాష్ తొలగించబడింది (Local Cache Cleared)"))
            }
        }
    }
}
