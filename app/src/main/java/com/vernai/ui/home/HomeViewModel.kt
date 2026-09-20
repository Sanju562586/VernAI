package com.vernai.ui.home

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.vernai.core.database.VernAiDatabase
import com.vernai.core.model.Language
import com.vernai.core.preferences.UserPreferencesManager
import com.vernai.ui.common.MviViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class HomeViewModel(
    private val database: VernAiDatabase? = null,
    private val context: Context? = null
) : MviViewModel<HomeUiState, HomeUiIntent, HomeUiSideEffect>(
    run {
        val initialLang = if (context != null) {
            UserPreferencesManager.getPreferredLanguage(context)
        } else {
            Language.TELUGU
        }
        HomeUiState(
            activeLanguage = initialLang,
            features = getFeaturesForLanguage(initialLang)
        )
    }
) {

    init {
        refreshHistoryStats()
        if (context != null) {
            viewModelScope.launch {
                UserPreferencesManager.preferredLanguageFlow.collect { lang ->
                    if (lang != null && lang != currentState.activeLanguage) {
                        setState {
                            copy(
                                activeLanguage = lang,
                                features = getFeaturesForLanguage(lang)
                            )
                        }
                    }
                }
            }
        }
    }

    override fun handleIntent(intent: HomeUiIntent) {
        when (intent) {
            is HomeUiIntent.SelectLanguage -> {
                if (context != null) {
                    UserPreferencesManager.setPreferredLanguage(context, intent.language)
                }
                setState {
                    copy(
                        activeLanguage = intent.language,
                        features = getFeaturesForLanguage(intent.language)
                    )
                }
            }
            is HomeUiIntent.NavigateTo -> {
                sendSideEffect(HomeUiSideEffect.Navigate(intent.destination))
            }
            is HomeUiIntent.RefreshStats -> {
                refreshHistoryStats()
            }
        }
    }

    private fun refreshHistoryStats() {
        val db = database ?: return
        viewModelScope.launch {
            val salesCount = runCatching { db.salesLogDao().getAllSalesLogs().firstOrNull()?.size ?: 0 }.getOrDefault(0)
            val complaintCount = runCatching { db.complaintDao().getAllComplaints().firstOrNull()?.size ?: 0 }.getOrDefault(0)
            val docCount = runCatching { db.explanationDao().getAllExplanations().firstOrNull()?.size ?: 0 }.getOrDefault(0)

            setState {
                copy(
                    totalSalesRecorded = salesCount,
                    totalComplaintsDrafted = complaintCount,
                    totalDocumentsRead = docCount
                )
            }
        }
    }
}
