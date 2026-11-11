package ru.slicoo.code_droid.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.slicoo.code_droid.core.data.WorkspaceRepository
import ru.slicoo.code_droid.core.model.WorkspaceInfo
import java.io.File

data class WorkspaceUiState(
    val workspaces: List<WorkspaceInfo> = emptyList(),
    val activePath: String? = null,
    val isImporting: Boolean = false,
    val error: String? = null,
)

/**
 * Backing state for the workspace screen: list of local projects, creating
 * new ones and importing an existing folder via SAF.
 */
class WorkspaceViewModel(
    private val repository: WorkspaceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(
            workspaces = repository.listWorkspaces(),
            activePath = repository.activeWorkspaceInfo()?.path,
        )
    }

    fun create(name: String) {
        if (name.isBlank()) return
        repository.createWorkspace(name)
        refresh()
    }

    fun select(info: WorkspaceInfo) {
        val dir = File(info.path)
        if (!dir.isDirectory) {
            _uiState.value = _uiState.value.copy(
                error = "Папка проекта не найдена на устройстве: ${info.displayName}",
            )
            return
        }
        repository.setActiveWorkspace(info)
        refresh()
    }

    fun importFromUri(uri: android.net.Uri, displayName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, error = null)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    repository.importFromUri(uri, displayName)
                }
            }
            _uiState.value = when {
                result.isSuccess -> _uiState.value.copy(isImporting = false)
                else -> _uiState.value.copy(
                    isImporting = false,
                    error = "Ошибка импорта: ${result.exceptionOrNull()?.message}",
                )
            }
            refresh()
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
