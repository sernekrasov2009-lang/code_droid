package ru.slicoo.code_droid.feature.chat

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ChatUiState(
    val messages: List<String> = emptyList(),
    val isGenerating: Boolean = false,
)

class ChatViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun send(text: String) {
        // Stage 1 (MVP chat) wires this to an OpenAI-compatible provider.
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + text,
        )
    }
}
