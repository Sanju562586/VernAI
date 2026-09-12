package com.vernai.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

interface UiState
interface UiIntent
interface UiSideEffect

/**
 * Base Pragmatic MVI ViewModel for VernAI Jetpack Compose screens.
 * Enforces Unidirectional Data Flow (UDF):
 * View -> Intent -> ViewModel -> Reducer -> State -> View
 * View -> SideEffect (One-shot event like SnackBar, Toast, Navigation)
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiSideEffect>(
    initialState: S
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _sideEffect = Channel<E>(Channel.BUFFERED)
    val sideEffect = _sideEffect.receiveAsFlow()

    /**
     * Entry point for all UI interactions.
     */
    abstract fun handleIntent(intent: I)

    protected fun setState(reducer: S.() -> S) {
        _uiState.value = _uiState.value.reducer()
    }

    protected fun sendSideEffect(effect: E) {
        viewModelScope.launch {
            _sideEffect.send(effect)
        }
    }
}
