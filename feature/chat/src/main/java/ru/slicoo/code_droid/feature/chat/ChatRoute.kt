package ru.slicoo.code_droid.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.slicoo.code_droid.core.model.Message
import ru.slicoo.code_droid.core.model.MessagePart
import ru.slicoo.code_droid.core.model.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoute(
    viewModel: ChatViewModel,
    onEnterKey: () -> Unit,
    onOpenWorkspace: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Keep the newest message in view while streaming.
    LaunchedEffect(state.messages.size, state.streamingText) {
        val last = state.messages.size + if (state.streamingText != null) 1 else 0
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    // Re-read config labels every time the screen re-enters composition
    // (e.g. after picking a workspace or saving settings).
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Code Droid") },
                actions = {
                    TextButton(onClick = onOpenWorkspace) {
                        Text(state.workspaceLabel ?: "Папка")
                    }
                    TextButton(onClick = onEnterKey) { Text("Настройки") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            ChatScreen(
                state = state,
                listState = listState,
                onSend = viewModel::send,
                onCancel = viewModel::cancel,
                onDismissError = viewModel::dismissError,
                onEnterKey = onEnterKey,
                modifier = Modifier.weight(1f),
            )
            state.modelLabel?.let { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
            state.workspaceLabel?.let { label ->
                Text(
                    "Папка: $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun ChatScreen(
    state: ChatUiState,
    listState: LazyListState,
    onSend: (String) -> Unit,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    onEnterKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }
            if (state.toolEvents.isNotEmpty()) {
                items(
                    state.toolEvents,
                    key = { it.hashCode() },
                ) { toolEvent ->
                    ToolEventRow(toolEvent)
                }
            }
            if (state.streamingText != null) {
                item(key = "streaming") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            state.streamingText.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            if (state.error != null) {
                item(key = "error") {
                    ErrorPanel(
                        message = state.error.orEmpty(),
                        needsKey = state.needsKey,
                        onDismissError = onDismissError,
                        onEnterKey = onEnterKey,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Напишите сообщение…") },
                enabled = !state.isGenerating,
            )
            if (state.isGenerating) {
                IconButton(onClick = onCancel) {
                    Text("✕")
                }
            } else {
                IconButton(onClick = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                }) {
                    Text("→")
                }
            }
        }
    }
}

@Composable
private fun ToolEventRow(toolEvent: ToolEventUi) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(0.85f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = if (toolEvent.isRunning) "⏳ ${toolEvent.name}" else "✓ ${toolEvent.name}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = toolEvent.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    needsKey: Boolean,
    onDismissError: () -> Unit,
    onEnterKey: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (needsKey) {
                Button(
                    onClick = onEnterKey,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Ввести API-ключ")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isUser = message.role == Role.USER
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = message.parts.filterIsInstance<MessagePart.Text>()
                    .joinToString("") { it.text },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
