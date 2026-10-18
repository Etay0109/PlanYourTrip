package com.example.planyourtrip.ui.tripdetails.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.Spacing

/** Text field and round send button; padded above the navigation bar and the keyboard. */
@Composable
internal fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    destination: String,
    sendEnabled: Boolean,
    onSend: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        stringResource(R.string.assistant_input_hint, destination),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // "950 / 1000" only close to the limit, where the field stops taking text.
                supportingText = if (text.length >= MAX_MESSAGE_CHARS - CHAR_COUNT_MARGIN) {
                    { Text(stringResource(R.string.assistant_char_count, text.length, MAX_MESSAGE_CHARS)) }
                } else {
                    null
                },
                maxLines = 4,
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { if (sendEnabled) onSend() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.m))
            FilledIconButton(
                onClick = onSend,
                enabled = sendEnabled,
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier.size(SendButtonSize),
            ) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.assistant_send))
            }
        }
    }
}

/** The message counter shows this many characters before [MAX_MESSAGE_CHARS]. */
private const val CHAR_COUNT_MARGIN = 100

/** The round Send button. */
private val SendButtonSize = 56.dp
