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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.sharing.InviteCode
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing

/** The sheet's title row: key badge, "Join a trip" with its subtitle, and Paste. */
@Composable
internal fun JoinHeader(isJoining: Boolean, onPaste: () -> Unit) {
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
        Spacer(Modifier.width(Spacing.l))
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
        Spacer(Modifier.width(Spacing.s))
        FilledTonalButton(onClick = onPaste, enabled = !isJoining) {
            Icon(painterResource(R.drawable.ic_content_paste), contentDescription = null, Modifier.size(IconSize.s))
            Spacer(Modifier.width(Spacing.xsPlus))
            Text(stringResource(R.string.join_paste))
        }
    }
}

/** Under the code: the trip it opens (with the privacy note), "Checking…", or why it can't be used. */
@Composable
internal fun CodeLookupStatus(state: JoinUiState, onRetry: () -> Unit) {
    when (val lookup = state.lookup) {
        is CodeLookup.Found -> {
            JoiningCard(lookup.preview)
            Spacer(Modifier.height(Spacing.l))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painterResource(R.drawable.ic_shield),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.m),
                )
                Spacer(Modifier.width(Spacing.m))
                Text(
                    text = stringResource(R.string.join_privacy),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        CodeLookup.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = Dimens.ProgressStroke)
            Spacer(Modifier.width(Spacing.m))
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
        Spacer(Modifier.height(Spacing.s))
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
internal fun CodeBoxes(code: String, focused: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xsPlus),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(InviteCode.LENGTH) { index ->
            if (index == InviteCode.LENGTH / 2) {
                Box(
                    Modifier
                        .width(CodeDashWidth)
                        .height(CodeDashHeight)
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
                        if (current) Modifier.border(CurrentCellBorder, MaterialTheme.colorScheme.primary, shape) else Modifier,
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

/** The dash between the two halves of the code boxes. */
private val CodeDashWidth = 6.dp
private val CodeDashHeight = 2.dp

/** The ring of the code box the next character goes into. */
private val CurrentCellBorder = 2.dp
