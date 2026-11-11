package ru.slicoo.code_droid.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.slicoo.code_droid.core.data.AppConfigRepository
import ru.slicoo.code_droid.core.data.ChatConfig
import ru.slicoo.code_droid.core.llm.LlmHttpException
import ru.slicoo.code_droid.core.model.ModelConfig
import ru.slicoo.code_droid.core.model.ProviderConfig

data class SettingsUiState(
    val providers: List<ProviderConfig> = emptyList(),
    val activeProviderId: String? = null,
    val selectedProviderId: String? = null,
    val apiKeyInput: String = "",
    val savedKeyPresent: Boolean = false,
    val selectedModel: String? = null,
    val models: List<ModelConfig> = emptyList(),
    val isChecking: Boolean = false,
    val isFetchingModels: Boolean = false,
    val error: String? = null,
    val status: String? = null,
    val showAddProvider: Boolean = false,
    val draftName: String = "",
    val draftBaseUrl: String = "",
    val draftModel: String = "",
)

class SettingsViewModel(
    private val repository: AppConfigRepository,
    private val chatConfig: ChatConfig,
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadState(): SettingsUiState {
        val config = repository.load()
        val activeId = config.activeProviderId
        val selected = config.providerById(activeId ?: config.providers.firstOrNull()?.id ?: "")
        return SettingsUiState(
            providers = config.providers,
            activeProviderId = activeId,
            selectedProviderId = selected?.id,
            apiKeyInput = "",
            savedKeyPresent = selected?.let { repository.apiKey(it.id) != null } == true,
            selectedModel = selected?.let { config.accountFor(it.id)?.selectedModel }
                ?: selected?.models?.firstOrNull()?.id,
            models = selected?.models ?: emptyList(),
        )
    }

    fun selectProvider(providerId: String) {
        val config = repository.load()
        val provider = config.providerById(providerId) ?: return
        _uiState.value = _uiState.value.copy(
            selectedProviderId = providerId,
            apiKeyInput = "",
            savedKeyPresent = repository.apiKey(providerId) != null,
            selectedModel = config.accountFor(providerId)?.selectedModel
                ?: provider.models.firstOrNull()?.id,
            models = provider.models,
            error = null,
            status = null,
        )
    }

    fun onApiKeyInput(value: String) {
        _uiState.value = _uiState.value.copy(apiKeyInput = value, error = null, status = null)
    }

    fun selectModel(modelId: String) {
        val providerId = _uiState.value.selectedProviderId ?: return
        repository.setSelectedModel(providerId, modelId)
        _uiState.value = _uiState.value.copy(selectedModel = modelId)
    }

    /** Saves the key and makes this provider active. */
    fun saveKey() {
        val state = _uiState.value
        val providerId = state.selectedProviderId ?: return
        val key = state.apiKeyInput.trim()
        if (key.isEmpty()) {
            removeKey()
            return
        }
        repository.writeApiKey(providerId, key)
        repository.setActiveProvider(providerId)
        repository.setSelectedModel(providerId, state.selectedModel ?: return)
        _uiState.value = state.copy(
            apiKeyInput = "",
            savedKeyPresent = true,
            activeProviderId = providerId,
            status = "Ключ сохранён, провайдер активен.",
            error = null,
        )
    }

    fun removeKey() {
        val providerId = _uiState.value.selectedProviderId ?: return
        repository.removeApiKey(providerId)
        _uiState.value = _uiState.value.copy(
            apiKeyInput = "",
            savedKeyPresent = false,
            status = "Ключ удалён.",
        )
    }

    fun checkKey() {
        val state = _uiState.value
        val provider = state.providers.firstOrNull { it.id == state.selectedProviderId } ?: return
        val key = state.apiKeyInput.trim().ifEmpty { repository.apiKey(provider.id) ?: "" }
        if (key.isEmpty()) {
            _uiState.value = state.copy(error = "Введите ключ, чтобы проверить его.")
            return
        }
        _uiState.value = state.copy(isChecking = true, error = null, status = null)
        viewModelScope.launch {
            try {
                chatConfig.modelCatalog.fetchOpenAiCompatible(provider.baseUrl, key)
                _uiState.value = _uiState.value.copy(
                    isChecking = false,
                    status = "Ключ работает.",
                )
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

    fun refreshModels() {
        val state = _uiState.value
        val provider = state.providers.firstOrNull { it.id == state.selectedProviderId } ?: return
        val key = state.apiKeyInput.trim().ifEmpty { repository.apiKey(provider.id) ?: "" }
        if (key.isEmpty()) {
            _uiState.value = state.copy(error = "Введите ключ, чтобы загрузить модели.")
            return
        }
        _uiState.value = state.copy(isFetchingModels = true, error = null, status = null)
        viewModelScope.launch {
            try {
                val models = chatConfig.modelCatalog.fetchOpenAiCompatible(provider.baseUrl, key)
                if (models.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isFetchingModels = false,
                        error = "Сервис вернул пустой список моделей.",
                    )
                } else {
                    repository.replaceModels(provider.id, models)
                    _uiState.value = _uiState.value.copy(
                        isFetchingModels = false,
                        models = models,
                        status = "Загружено моделей: ${models.size}.",
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isFetchingModels = false,
                    error = friendlyError(e),
                )
            }
        }
    }

    fun showAddProvider(show: Boolean) {
        _uiState.value = _uiState.value.copy(
            showAddProvider = show,
            draftName = if (show) _uiState.value.draftName else "",
            draftBaseUrl = if (show) _uiState.value.draftBaseUrl else "",
            draftModel = if (show) _uiState.value.draftModel else "",
        )
    }

    fun onDraftChange(name: String, baseUrl: String, model: String) {
        _uiState.value = _uiState.value.copy(
            draftName = name,
            draftBaseUrl = baseUrl,
            draftModel = model,
        )
    }

    fun addCustomProvider() {
        val state = _uiState.value
        val name = state.draftName.trim()
        var baseUrl = state.draftBaseUrl.trim()
        val model = state.draftModel.trim()
        if (name.isEmpty() || baseUrl.isEmpty()) {
            _uiState.value = state.copy(error = "Заполните название и Base URL.")
            return
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://$baseUrl"
        }
        baseUrl = baseUrl.trimEnd('/')
        val id = "custom:" + name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
        val provider = ProviderConfig(
            id = id,
            name = name,
            baseUrl = baseUrl,
            models = if (model.isEmpty()) emptyList() else listOf(ModelConfig(model)),
        )
        repository.upsertCustomProvider(provider)
        _uiState.value = loadState().copy(
            selectedProviderId = id,
            status = "Провайдер «$name» добавлен. Введите ключ ниже.",
        )
    }

    fun removeProvider() {
        val providerId = _uiState.value.selectedProviderId ?: return
        if (providerId.startsWith("custom:").not()) return
        repository.removeProvider(providerId)
        _uiState.value = loadState().copy(status = "Провайдер удалён.")
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearStatus() {
        _uiState.value = _uiState.value.copy(status = null)
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is LlmHttpException -> when (e.statusCode) {
            401, 403 -> "Ключ неверен или не подходит для этого сервиса (HTTP ${e.statusCode})."
            429 -> "Слишком много запросов. Попробуйте позже."
            else -> "Сервис ответил ошибкой (HTTP ${e.statusCode})."
        }
        is java.net.UnknownHostException -> "Нет соединения с сервером. Проверьте сеть или адрес."
        else -> "Ошибка: ${e.message ?: e.javaClass.simpleName}"
    }
}
