package ru.slicoo.code_droid.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import ru.slicoo.code_droid.core.data.AppConfigRepository
import ru.slicoo.code_droid.core.data.ChatConfig
import ru.slicoo.code_droid.core.llm.LlmHttpException
import ru.slicoo.code_droid.core.model.ProviderConfig

data class OnboardingUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val providerId: String? = null,
    val apiKey: String = "",
    val isChecking: Boolean = false,
    val error: String? = null,
)

class OnboardingViewModel(
    private val repository: AppConfigRepository,
    private val chatConfig: ChatConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private fun initialState(): OnboardingUiState {
        val config = repository.load()
        return OnboardingUiState(
            providers = config.providers,
            providerId = config.providers.firstOrNull()?.id,
        )
    }

    fun onProviderChange(provider: ProviderConfig) {
        _uiState.value = _uiState.value.copy(providerId = provider.id, error = null)
    }

    fun onApiKeyChange(value: String) {
        _uiState.value = _uiState.value.copy(apiKey = value, error = null)
    }

    fun checkAndSave() {
        val state = _uiState.value
        val key = state.apiKey.trim()
        val provider = state.providers.firstOrNull { it.id == state.providerId } ?: return
        if (key.isEmpty() || state.isChecking) return
        _uiState.value = state.copy(isChecking = true, error = null)

        viewModelScope.launch {
            try {
                // A /models call proves key and reachability in one cheap request.
                chatConfig.modelCatalog.fetchOpenAiCompatible(provider.baseUrl, key)
                repository.writeApiKey(provider.id, key)
                repository.setActiveProvider(provider.id)
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
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Code Droid", style = MaterialTheme.typography.titleLarge)
        Text(
            "AI-среда разработки на Android",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text(
            "Выберите провайдера",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp),
        )
        FlowChips(
            providers = state.providers,
            selectedId = state.providerId,
            onSelect = viewModel::onProviderChange,
        )

        OutlinedTextField(
            value = state.apiKey,
            onValueChange = viewModel::onApiKeyChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            label = { Text("API-ключ") },
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
            Text("Пропустить и настроить позже")
        }
    }
}

/** Chip list of providers, two-column rows to keep it compact. */
@Composable
private fun FlowChips(
    providers: List<ProviderConfig>,
    selectedId: String?,
    onSelect: (ProviderConfig) -> Unit,
) {
    providers.chunked(2).forEach { rowProviders ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowProviders.forEach { provider ->
                FilterChip(
                    selected = selectedId == provider.id,
                    onClick = { onSelect(provider) },
                    label = { Text(provider.name, maxLines = 1) },
                )
            }
        }
    }
}
