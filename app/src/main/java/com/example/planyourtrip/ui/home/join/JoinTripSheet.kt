package com.example.planyourtrip.ui.home.join

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.sharing.InviteCode
import com.example.planyourtrip.data.sharing.InvitePreview
import com.example.planyourtrip.ui.common.components.HeroWaves
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.common.datesWithLength
import com.example.planyourtrip.ui.common.split.avatarLetter
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.theme.PlanYourTripTheme
import java.time.LocalDate
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
            .padding(bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(Dimens.AvatarSize)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_key),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.join_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.join_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    scope.launch {
                        val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                            ?.getItemAt(0)?.text?.toString() ?: return@launch
                        setCode(InviteCode.extract(text) ?: InviteCode.normalize(text))
                    }
                },
                enabled = !state.isJoining,
            ) {
                Icon(painterResource(R.drawable.ic_content_paste), contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.join_paste))
            }
        }
        Spacer(Modifier.height(20.dp))

        var focused by remember { mutableStateOf(false) }
        val codeDescription = stringResource(R.string.join_code_description, field.text.length)
        BasicTextField(
            value = field,
            onValueChange = { new ->
                if (state.isJoining) return@BasicTextField
                setCode(InviteCode.input(field.text, new.text))
            },
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
        Spacer(Modifier.height(16.dp))

        when (val lookup = state.lookup) {
            is CodeLookup.Found -> {
                JoiningCard(lookup.preview)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        painterResource(R.drawable.ic_shield),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.join_privacy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CodeLookup.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.join_checking),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CodeLookup.Invalid -> if (state.error == null) Message(stringResource(R.string.join_invalid_code))
            CodeLookup.Failed -> Column {
                Message(stringResource(R.string.join_failed))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.assistant_try_again)) }
            }
            CodeLookup.Incomplete -> Unit
        }
        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Message(
                stringResource(
                    when (error) {
                        JoinError.INVALID_CODE -> R.string.join_invalid_code
                        JoinError.ALREADY_MEMBER -> R.string.join_already_member
                        JoinError.TRIP_FULL -> R.string.join_trip_full
                        JoinError.FAILED -> R.string.join_failed
                    },
                ),
            )
        }
        Spacer(Modifier.height(20.dp))
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

/** Errors are announced as they appear. */
@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** 8 boxes, a short dash between the two groups of four; the next box to fill is outlined while focused. */
@Composable
private fun CodeBoxes(code: String, focused: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(InviteCode.LENGTH) { index ->
            if (index == InviteCode.LENGTH / 2) {
                Box(
                    Modifier
                        .width(6.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            val current = focused && index == code.length.coerceAtMost(InviteCode.LENGTH - 1)
            val shape = MaterialTheme.shapes.small
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.82f)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .then(
                        if (current) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier,
                    ),
            ) {
                Text(
                    text = code.getOrNull(index)?.toString().orEmpty(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** "YOU'RE JOINING": the invite's trip on the hero gradient, with who invited me in the band at the bottom. */
@Composable
private fun JoiningCard(preview: InvitePreview) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.heroGradient)
            .semantics(mergeDescendants = true) {},
    ) {
        HeroWaves(Modifier.matchParentSize())
        Column {
            Column(Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.join_joining_label),
                        style = HeroLabelStyle,
                        color = colors.onHeroMuted,
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(colors.heroOverlay, CircleShape)
                            .border(1.dp, colors.heroOverlayBorder, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_verified),
                            contentDescription = null,
                            tint = colors.onHero,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.join_valid_code),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onHero,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = preview.destination,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.onHero,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = datesWithLength(preview.startDate, preview.endDate),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onHeroMuted,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.heroOverlay)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.inverseSurface, CircleShape),
                ) {
                    Text(
                        text = avatarLetter(preview.inviterName),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.join_invited_by, preview.inviterName),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onHero,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val previewInvite = InvitePreview(
    code = "K7M4XP2R",
    tripUuid = "t1",
    destination = "Rome, Italy",
    startDate = LocalDate.of(2026, 10, 19),
    endDate = LocalDate.of(2026, 10, 24),
    inviterName = "Noa",
)

@Preview(showBackground = true)
@Composable
private fun JoinTripFoundPreview() {
    PlanYourTripTheme(darkTheme = false) {
        JoinTripContent(
            state = JoinUiState(isOpen = true, code = "K7M4XP2R", lookup = CodeLookup.Found(previewInvite)),
            onCodeChange = {}, onRetry = {}, onJoin = {}, onDismiss = {}, initialCode = "K7M4XP2R",
        )
    }
}

@Preview(showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun JoinTripNarrowPreview() {
    PlanYourTripTheme(darkTheme = false) {
        JoinTripContent(
            state = JoinUiState(isOpen = true, code = "K7M4XP2R", lookup = CodeLookup.Found(previewInvite)),
            onCodeChange = {}, onRetry = {}, onJoin = {}, onDismiss = {}, initialCode = "K7M4XP2R",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun JoinTripInvalidPreview() {
    PlanYourTripTheme(darkTheme = true) {
        JoinTripContent(
            state = JoinUiState(isOpen = true, code = "K7M4XP2R", lookup = CodeLookup.Invalid),
            onCodeChange = {}, onRetry = {}, onJoin = {}, onDismiss = {}, initialCode = "K7M4XP2R",
        )
    }
}
