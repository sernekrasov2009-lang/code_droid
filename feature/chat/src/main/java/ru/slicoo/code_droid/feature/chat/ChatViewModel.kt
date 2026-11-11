package ru.slicoo.code_droid.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.slicoo.code_droid.core.data.ChatConfig
import ru.slicoo.code_droid.core.llm.LlmHttpException
import ru.slicoo.code_droid.core.llm.LlmProvider
import ru.slicoo.code_droid.core.llm.LlmRequest
import ru.slicoo.code_droid.core.llm.StreamEvent
import ru.slicoo.code_droid.core.model.Message
import ru.slicoo.code_droid.core.model.MessagePart
import ru.slicoo.code_droid.core.model.Role
import java.util.UUID

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val streamingText: String? = null,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val needsKey: Boolean = false,
    /** Human-readable label of the model currently in use, if configured. */
    val modelLabel: String? = null,
)

/**
 * Streams assistant replies into [ChatUiState], supports cancelling an
 * in-flight generation, and keeps the last error for the UI.
 */
class ChatViewModel(
    private val chatConfig: ChatConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(refreshState(ChatUiState()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    fun refresh() {
        _uiState.value = refreshState(_uiState.value)
    }

    private fun refreshState(state: ChatUiState): ChatUiState {
        val setup = chatConfig.activeSetup()
        return state.copy(
            modelLabel = setup?.let { (provider, account) ->
                "${provider.name} · ${account.selectedModel}"
            },
            needsKey = setup == null,
        )
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _uiState.value.isGenerating) return
        val setup = chatConfig.activeSetup()
        if (setup == null) {
            _uiState.value = _uiState.value.copy(
                error = "Провайдер не настроен: выберите его и введите API-ключ.",
                needsKey = true,
            )
            return
        }
        val (provider, account) = setup
        val llmProvider: LlmProvider = try {
            chatConfig.currentProvider() ?: run {
                _uiState.value = _uiState.value.copy(
                    error = "Провайдер не настроен.",
                    needsKey = true,
                )
                return
            }
        } catch (e: UnsupportedOperationException) {
            _uiState.value = _uiState.value.copy(
                error = e.message,
                needsKey = false,
            )
            return
        }

        val userMessage = Message(
            id = UUID.randomUUID().toString(),
            role = Role.USER,
            parts = listOf(MessagePart.Text(trimmed)),
            createdAt = System.currentTimeMillis(),
        )
        val history = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(
            messages = history,
            streamingText = "",
            isGenerating = true,
            error = null,
            needsKey = false,
        )

        generationJob = viewModelScope.launch {
            val assistantText = StringBuilder()
            try {
                llmProvider.chatStream(
                    LlmRequest(
                        model = account.selectedModel,
                        messages = history,
                    ),
                ).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> {
                            assistantText.append(event.text)
                            _uiState.value = _uiState.value.copy(
                                streamingText = assistantText.toString(),
                            )
                        }
                        StreamEvent.Done -> Unit
                        is StreamEvent.Usage -> Unit
                        is StreamEvent.Error -> throw event.throwable
                    }
                }
                if (assistantText.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        messages = _uiState.value.messages + assistantMessage(assistantText.toString()),
                        streamingText = null,
                        isGenerating = false,
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        streamingText = null,
                        isGenerating = false,
                        error = "Пустой ответ от модели. Проверьте ключ и модель.",
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // User cancelled: keep whatever streamed so far.
                val partial = assistantText.toString()
                _uiState.value = _uiState.value.copy(
                    messages = if (partial.isEmpty()) _uiState.value.messages else
                        _uiState.value.messages + assistantMessage(partial),
                    streamingText = null,
                    isGenerating = false,
                    error = null,
                )
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    streamingText = null,
                    isGenerating = false,
                    error = errorMessage(e),
                )
            }
        }
    }

    private fun assistantMessage(text: String) = Message(
        id = UUID.randomUUID().toString(),
        role = Role.ASSISTANT,
        parts = listOf(MessagePart.Text(text)),
        createdAt = System.currentTimeMillis(),
    )

    fun cancel() {
        generationJob?.cancel()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null, needsKey = false)
    }

    private fun errorMessage(e: Throwable): String = when (e) {
        is LlmHttpException -> when (e.statusCode) {
            401, 403 -> "API-ключ неверен или без доступа (HTTP ${e.statusCode})."
            429 -> "Лимит запросов исчерпан (HTTP 429). Попробуйте позже."
            400 -> "Запрос отклонён (HTTP 400). Возможно, неверное имя модели."
            else -> "Ошибка провайдера: HTTP ${e.statusCode}."
        }
        is java.net.UnknownHostException -> "Нет соединения с сервером. Проверьте сеть."
        else -> "Ошибка: ${e.message ?: e.javaClass.simpleName}"
    }
}
