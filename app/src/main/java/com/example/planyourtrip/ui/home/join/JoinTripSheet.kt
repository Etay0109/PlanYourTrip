package com.example.planyourtrip.ui.home.join

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.example.planyourtrip.R
import com.example.planyourtrip.data.sharing.InviteCode
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.Spacing
import kotlinx.coroutines.launch

/** The join sheet while [JoinTripViewModel] says it is open. */
@Composable
fun JoinTripHost(viewModel: JoinTripViewModel, state: JoinUiState) {
    if (!state.isOpen) return
    JoinTripSheet(
        state = state,
        onCodeChange = viewModel::onCodeChange,
        onRetry = viewModel::onRetry,
        onJoin = viewModel::onJoin,
        onDismiss = viewModel::onDismiss,
    )
}

/**
 * "Join a trip" (design/home_join.png): 8 character boxes with Paste; a valid code shows its trip (destination, dates,
 * who invited me), and only then "Join trip" is enabled. The typed code is Compose state (like the converter),
 * the lookup and the join are the ViewModel's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinTripSheet(
    state: JoinUiState,
    onCodeChange: (String) -> Unit,
    onRetry: () -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { if (!state.isJoining) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { !state.isJoining },
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        JoinTripContent(state, onCodeChange, onRetry, onJoin, onDismiss)
    }
}

@Composable
fun JoinTripContent(
    state: JoinUiState,
    onCodeChange: (String) -> Unit,
    onRetry: () -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
    initialCode: String = "",
) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialCode, TextRange(initialCode.length)))
    }
    fun setCode(code: String) {
        field = TextFieldValue(code, TextRange(code.length))
        onCodeChange(code)
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.ScreenPadding)
            .navigationBarsPadding()
            .padding(bottom = Spacing.l),
    ) {
        JoinHeader(
            isJoining = state.isJoining,
            onPaste = {
                scope.launch {
                    val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.text?.toString() ?: return@launch
                    setCode(InviteCode.extract(text) ?: InviteCode.normalize(text))
                }
            },
        )
        Spacer(Modifier.height(Spacing.xl))

        CodeField(
            field = field,
            onValueChange = { new ->
                if (!state.isJoining) setCode(InviteCode.input(field.text, new.text))
            },
        )
        Spacer(Modifier.height(Spacing.l))

        CodeLookupStatus(state, onRetry)
        Spacer(Modifier.height(Spacing.xl))
        PrimaryButton(
            text = stringResource(R.string.join_trip),
            onClick = onJoin,
            enabled = state.canJoin,
            loading = state.isJoining,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(
            onClick = onDismiss,
            enabled = !state.isJoining,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.TouchTarget),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

/** The invite code, typed into the 8 boxes ([CodeBoxes]) on an ASCII keyboard in capitals, without autocorrect. */
@Composable
private fun CodeField(field: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val codeDescription = stringResource(R.string.join_code_description, field.text.length)
    BasicTextField(
        value = field,
        onValueChange = onValueChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .semantics { contentDescription = codeDescription },
        decorationBox = { CodeBoxes(field.text, focused) },
    )
}
