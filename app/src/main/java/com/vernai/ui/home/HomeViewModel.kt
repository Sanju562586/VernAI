package com.vernai.ui.home

import androidx.lifecycle.viewModelScope
import com.vernai.core.database.VernAiDatabase
import com.vernai.ui.common.MviViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class HomeViewModel(
    private val database: VernAiDatabase? = null
) : MviViewModel<HomeUiState, HomeUiIntent, HomeUiSideEffect>(HomeUiState()) {

    init {
        refreshHistoryStats()
    }

    override fun handleIntent(intent: HomeUiIntent) {
        when (intent) {
            is HomeUiIntent.SelectLanguage -> {
                setState { copy(activeLanguage = intent.language) }
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
