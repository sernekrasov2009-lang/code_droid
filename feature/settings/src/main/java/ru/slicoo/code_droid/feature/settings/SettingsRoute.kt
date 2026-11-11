package ru.slicoo.code_droid.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.slicoo.code_droid.core.model.ProviderConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Назад") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Провайдер", style = MaterialTheme.typography.titleMedium)
            }
            items(state.providers, key = { it.id }) { provider ->
                ProviderCard(
                    provider = provider,
                    isActive = state.activeProviderId == provider.id,
                    isSelected = state.selectedProviderId == provider.id,
                    onSelect = { viewModel.selectProvider(provider.id) },
                )
            }
            item {
                TextButton(onClick = { viewModel.showAddProvider(true) }) {
                    Text("+ Добавить провайдера")
                }
            }
            if (state.showAddProvider) {
                item {
                    AddProviderForm(
                        name = state.draftName,
                        baseUrl = state.draftBaseUrl,
                        model = state.draftModel,
                        onChange = viewModel::onDraftChange,
                        onAdd = viewModel::addCustomProvider,
                        onCancel = { viewModel.showAddProvider(false) },
                    )
                }
            }

            item {
                Text("API-ключ", style = MaterialTheme.typography.titleMedium)
            }
            item {
                OutlinedTextField(
                    value = state.apiKeyInput,
                    onValueChange = viewModel::onApiKeyInput,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(if (state.savedKeyPresent) "Новый ключ (заменит текущий)" else "API-ключ")
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::saveKey, enabled = true) {
                        Text("Сохранить")
                    }
                    TextButton(onClick = viewModel::checkKey) {
                        if (state.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Проверить")
                        }
                    }
                    if (state.savedKeyPresent) {
                        TextButton(onClick = viewModel::removeKey) { Text("Удалить") }
                    }
                }
            }

            item {
                Text("Модель", style = MaterialTheme.typography.titleMedium)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = viewModel::refreshModels) {
                        if (state.isFetchingModels) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Обновить список")
                        }
                    }
                }
            }
            if (state.models.isEmpty()) {
                item {
                    Text(
                        "Модели не загружены. Нажмите «Обновить список» или введите модель вручную.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(state.models, key = { it.id }) { model ->
                    FilterChip(
                        selected = state.selectedModel == model.id,
                        onClick = { viewModel.selectModel(model.id) },
                        label = { Text(model.id) },
                    )
                }
            }
            if (state.selectedProviderId?.startsWith("custom:") == true) {
                item {
                    TextButton(onClick = viewModel::removeProvider) {
                        Text("Удалить этого провайдера", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (state.error != null) {
                item {
                    Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            state.error ?: "",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (state.status != null) {
                item {
                    Text(
                        state.status ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    isActive: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(provider.name, style = MaterialTheme.typography.titleSmall)
                if (isActive) {
                    AssistChip(onClick = {}, label = { Text("активен") })
                }
            }
            Text(
                provider.baseUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            provider.notes?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AddProviderForm(
    name: String,
    baseUrl: String,
    model: String,
    onChange: (String, String, String) -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Свой провайдер (OpenAI-совместимый)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = name,
                onValueChange = { onChange(it, baseUrl, model) },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { onChange(name, it, model) },
                label = { Text("Base URL (напр. https://example.com/v1)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { onChange(name, baseUrl, it) },
                label = { Text("Модель по умолчанию (можно пустым)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd, enabled = name.isNotBlank() && baseUrl.isNotBlank()) {
                    Text("Добавить")
                }
                TextButton(onClick = onCancel) { Text("Отмена") }
            }
        }
    }
}
