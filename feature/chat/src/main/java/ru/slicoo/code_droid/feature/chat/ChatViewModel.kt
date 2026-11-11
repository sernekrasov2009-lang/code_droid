package ru.slicoo.code_droid.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.slicoo.code_droid.core.agent.AgentEvent
import ru.slicoo.code_droid.core.agent.AgentLoop
import ru.slicoo.code_droid.core.agent.ToolApproval
import ru.slicoo.code_droid.core.agent.WorkspaceTools
import ru.slicoo.code_droid.core.data.ChatConfig
import ru.slicoo.code_droid.core.llm.LlmHttpException
import ru.slicoo.code_droid.core.model.Message
import ru.slicoo.code_droid.core.model.MessagePart
import ru.slicoo.code_droid.core.model.Role
import ru.slicoo.code_droid.core.workspace.WorkspaceManager
import java.util.UUID

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val streamingText: String? = null,
    val isGenerating: Boolean = false,
    val error: String? = null,
    val needsKey: Boolean = false,
    val modelLabel: String? = null,
    val workspaceLabel: String? = null,
    /** Tool activity log for the current turn: name + status. */
    val toolEvents: List<ToolEventUi> = emptyList(),
)

data class ToolEventUi(
    val name: String,
    val detail: String,
    val isRunning: Boolean,
)

/**
 * Chat screen backend. Plain chat without a workspace; the agent loop with
 * tools when a workspace is active.
 */
class ChatViewModel(
    private val chatConfig: ChatConfig,
    private val workspaceProvider: () -> WorkspaceManager?,
) : ViewModel() {

    private val _uiState = MutableStateFlow(refreshState(ChatUiState()))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var generationJob: Job? = null

    fun refresh() {
        _uiState.value = refreshState(_uiState.value)
    }

    private fun refreshState(state: ChatUiState): ChatUiState {
        val setup = chatConfig.activeSetup()
        val workspaceManager = workspaceProvider()
        return state.copy(
            modelLabel = setup?.let { (provider, account) ->
                "${provider.name} · ${account.selectedModel}"
            },
            needsKey = setup == null,
            workspaceLabel = workspaceManager?.rootPath?.name,
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
        val (_, account) = setup
        val llmProvider = try {
            chatConfig.currentProvider() ?: return
        } catch (e: UnsupportedOperationException) {
            _uiState.value = _uiState.value.copy(error = e.message)
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
            toolEvents = emptyList(),
        )

        generationJob = viewModelScope.launch {
            val assistantText = StringBuilder()
            try {
                val workspaceManager = workspaceProvider()
                val loop = if (workspaceManager != null) {
                    AgentLoop(
                        provider = llmProvider,
                        tools = WorkspaceTools(workspaceManager),
                        approval = autoApproval,
                    )
                } else {
                    null
                }
                val finalTranscript: List<Message>
                if (loop != null) {
                    finalTranscript = loop.run(account.selectedModel, history) { event ->
                        applyAgentEvent(event, assistantText)
                    }
                } else {
                    // Plain chat, no tools.
                    llmProvider.chatStream(
                        ru.slicoo.code_droid.core.llm.LlmRequest(
                            model = account.selectedModel,
                            messages = history,
                        ),
                    ).collect { event ->
                        when (event) {
                            is ru.slicoo.code_droid.core.llm.StreamEvent.TextDelta -> {
                                assistantText.append(event.text)
                                _uiState.value = _uiState.value.copy(
                                    streamingText = assistantText.toString(),
                                )
                            }
                            is ru.slicoo.code_droid.core.llm.StreamEvent.Error ->
                                throw event.throwable
                            else -> Unit
                        }
                    }
                    finalTranscript = history + assistantMessage(assistantText.toString())
                }

                _uiState.value = _uiState.value.copy(
                    messages = finalTranscript,
                    streamingText = null,
                    isGenerating = false,
                    toolEvents = emptyList(),
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
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

    /** Auto-approve non-mutating tools; mutating ones ask the user later (Stage 3 UI). */
    private val autoApproval = ToolApproval { _, _ -> true }

    private fun applyAgentEvent(event: AgentEvent, assistantText: StringBuilder) {
        when (event) {
            is AgentEvent.TextDelta -> {
                assistantText.append(event.text)
                _uiState.value = _uiState.value.copy(
                    streamingText = assistantText.toString(),
                )
            }
            is AgentEvent.ToolStarted -> {
                _uiState.value = _uiState.value.copy(
                    toolEvents = _uiState.value.toolEvents + ToolEventUi(
                        name = event.name,
                        detail = event.arguments.take(120),
                        isRunning = true,
                    ),
                )
            }
            is AgentEvent.ToolFinished -> {
                _uiState.value = _uiState.value.copy(
                    toolEvents = _uiState.value.toolEvents
                        .filterNot { it.isRunning && it.name == event.name }
                        .let {
                            it + ToolEventUi(
                                name = event.name,
                                detail = event.result.take(200),
                                isRunning = false,
                            )
                        },
                )
            }
            is AgentEvent.Usage, is AgentEvent.TurnFinished -> Unit
            is AgentEvent.Error -> {
                _uiState.value = _uiState.value.copy(error = event.message)
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
