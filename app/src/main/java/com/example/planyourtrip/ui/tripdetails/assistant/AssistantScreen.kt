package com.example.planyourtrip.ui.tripdetails.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.Spacing

/** [viewModel] is scoped to the trip screen (AppNavHost), so the conversation outlives this screen. */
@Composable
fun AssistantScreen(onBack: () -> Unit, viewModel: AssistantViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AssistantContent(
        state = state,
        onBack = onBack,
        onSend = viewModel::onSend,
        onRetry = viewModel::onRetry,
        onToggleSuggestion = viewModel::onToggleSuggestion,
        onAddPlan = viewModel::onAddPlan,
    )
}

@Composable
fun AssistantContent(
    state: AssistantUiState,
    onBack: () -> Unit,
    /** Returns true when the message was sent, so the field can be cleared. */
    onSend: (String) -> Boolean,
    onRetry: () -> Unit,
    onToggleSuggestion: (messageId: Long, index: Int) -> Unit,
    onAddPlan: (messageId: Long) -> Unit,
) {
    // Typed text is Compose state: updated synchronously, never round-tripped through a StateFlow.
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val itemCount = 1 + state.messages.size + (if (state.isSending) 1 else 0) + (if (state.error != null) 1 else 0)
    // Follow the conversation: show the newest message, the typing indicator or the error.
    LaunchedEffect(state.messages.size, state.isSending, state.error) {
        if (state.messages.isNotEmpty() || state.isSending) listState.animateScrollToItem(itemCount - 1)
    }
    val canSend = state.isReady && !state.isSending

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopAppBar(onBack = onBack, title = stringResource(R.string.assistant_title)) },
        bottomBar = {
            InputBar(
                text = input,
                onTextChange = { input = it.take(MAX_MESSAGE_CHARS) },
                destination = state.trip?.destination.orEmpty(),
                sendEnabled = canSend && input.isNotBlank(),
                onSend = { if (onSend(input)) input = "" },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item(key = "intro") {
                if (state.messages.isEmpty()) {
                    Intro(state.chips, enabled = canSend, onChip = { onSend(it) })
                }
            }
            items(state.messages, key = { it.id }) { message ->
                when (message) {
                    is ChatMessage.User -> UserBubble(message.text)
                    is ChatMessage.Assistant -> AssistantAnswer(
                        message = message,
                        onToggle = { index -> onToggleSuggestion(message.id, index) },
                        onAdd = { onAddPlan(message.id) },
                    )
                }
            }
            if (state.isSending) {
                item(key = "typing") { TypingIndicator() }
            }
            state.error?.let { error ->
                item(key = "error") { ErrorMessage(error, onRetry) }
            }
        }
    }
}
