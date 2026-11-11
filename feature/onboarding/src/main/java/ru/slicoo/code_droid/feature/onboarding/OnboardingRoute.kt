package ru.slicoo.code_droid.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.slicoo.code_droid.core.data.ApiKeyStore
import ru.slicoo.code_droid.core.data.ChatConfig
import ru.slicoo.code_droid.core.llm.LlmHttpException
import ru.slicoo.code_droid.core.llm.LlmRequest
import ru.slicoo.code_droid.core.llm.StreamEvent
import ru.slicoo.code_droid.core.model.Message
import ru.slicoo.code_droid.core.model.MessagePart
import ru.slicoo.code_droid.core.model.Role

data class OnboardingUiState(
    val apiKey: String = "",
    val isChecking: Boolean = false,
    val error: String? = null,
    val alreadyHasKey: Boolean = false,
)

class OnboardingViewModel(
    private val keyStore: ApiKeyStore,
    private val chatConfig: ChatConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState(alreadyHasKey = keyStore.read() != null))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onApiKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(apiKey = value, error = null)
    }

    fun checkAndSave() {
        val key = _uiState.value.apiKey.trim()
        if (key.isEmpty() || _uiState.value.isChecking) return
        _uiState.value = _uiState.value.copy(isChecking = true, error = null)

        viewModelScope.launch {
            try {
                // Minimal request: 1 token proves both the key and reachability.
                val probe = LlmRequest(
                    model = chatConfig.defaultModel,
                    messages = listOf(
                        Message(
                            id = "probe",
                            role = Role.USER,
                            parts = listOf(MessagePart.Text("ping")),
                            createdAt = System.currentTimeMillis(),
                        ),
                    ),
                )
                var gotText = false
                chatConfig.providerFor(key).chatStream(probe).collect { event ->
                    if (event is StreamEvent.TextDelta && event.text.isNotEmpty()) {
                        gotText = true
                    }
                }
                if (!gotText) {
                    throw LlmHttpException(200, "empty response")
                }
                keyStore.write(key)
                _uiState.value = _uiState.value.copy(isChecking = false)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    error = friendlyError(e),
                )
            }
        }
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is LlmHttpException -> when (e.statusCode) {
            401, 403 -> "Ключ неверен или не подходит для этого сервиса."
            429 -> "Слишком много запросов. Попробуйте позже."
            else -> "Сервис ответил ошибкой (HTTP ${e.statusCode})."
        }
        is java.net.UnknownHostException -> "Нет соединения с сервером. Проверьте сеть."
        else -> "Ошибка: ${e.message ?: e.javaClass.simpleName}"
    }
}

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Code Droid", style = MaterialTheme.typography.titleLarge)
        Text(
            "AI-среда разработки на Android",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = viewModel::onApiKeyChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            label = { Text("API-ключ") },
            placeholder = { Text("sk-…") },
            isError = state.error != null,
            singleLine = true,
            visualTransformation = if (state.apiKey.isEmpty()) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        state.error?.let { error ->
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
        Button(
            onClick = viewModel::checkAndSave,
            enabled = !state.isChecking && state.apiKey.isNotBlank(),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            if (state.isChecking) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .height(20.dp)
                        .width(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Начать")
            }
        }
        TextButton(onClick = onDone) {
            Text("Пропустить")
        }
    }
}
