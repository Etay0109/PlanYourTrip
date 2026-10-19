package com.example.planyourtrip.ui.tripdetails.members

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.sharing.Invite
import com.example.planyourtrip.data.sharing.InviteCode
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.common.components.HeroWaves
import com.example.planyourtrip.ui.common.components.PrimaryButton
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
internal fun CodeSection(code: InviteCodeState, destination: String, actions: InviteSheetActions) {
    when (code) {
        InviteCodeState.Loading -> Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = CodeLoadingMinHeight),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        is InviteCodeState.Ready -> ReadyCode(code, destination, actions)
        InviteCodeState.Cancelled -> StatusWithAction(
            text = stringResource(R.string.invite_cancelled),
            action = stringResource(R.string.invite_new_code),
            onAction = actions.onNewCode,
        )
        InviteCodeState.Failed -> StatusWithAction(
            text = stringResource(R.string.invite_failed),
            action = stringResource(R.string.assistant_try_again),
            onAction = actions.onRetry,
        )
        InviteCodeState.Full -> Text(
            text = stringResource(R.string.invite_full),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The code card, when it expires and that it works once, then "Share invite" and "Cancel code". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadyCode(code: InviteCodeState.Ready, destination: String, actions: InviteSheetActions) {
    CodeCard(code.invite)
    Spacer(Modifier.height(Spacing.xl))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        InfoPill(R.drawable.ic_schedule, stringResource(R.string.invite_expires, TripFormatting.dayMonth(code.expiresOn)))
        InfoPill(R.drawable.ic_shield, stringResource(R.string.invite_works_once))
    }
    Spacer(Modifier.height(Spacing.xxl))
    val context = LocalContext.current
    val message = stringResource(
        R.string.invite_share_message,
        destination,
        InviteCode.forMessage(code.invite.code),
        TripFormatting.dayMonth(code.expiresOn),
    )
    val chooserTitle = stringResource(R.string.invite_share)
    PrimaryButton(
        text = stringResource(R.string.invite_share),
        onClick = { shareText(context, message, chooserTitle) },
        leadingIcon = R.drawable.ic_share,
        enabled = !code.isCancelling,
        modifier = Modifier.fillMaxWidth(),
    )
    TextButton(
        onClick = actions.onCancelCode,
        enabled = !code.isCancelling,
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.TouchTarget),
    ) {
        if (code.isCancelling) {
            CircularProgressIndicator(Modifier.size(IconSize.s), strokeWidth = Dimens.ProgressStroke)
        } else {
            Text(stringResource(R.string.invite_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusWithAction(text: String, action: String, onAction: () -> Unit) {
    Column {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.m))
        PrimaryButton(text = action, onClick = onAction, modifier = Modifier.fillMaxWidth())
    }
}

/** "INVITE CODE", the code in two groups of four, and the copy button, on the hero gradient. */
@Composable
private fun CodeCard(invite: Invite) {
    val colors = ExtendedTheme.colors
    val shape = MaterialTheme.shapes.extraLarge
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val shown = InviteCode.display(invite.code)
    // Read out character by character ("K 7 M 4 X P 2 R").
    val spoken = stringResource(R.string.invite_code_description, invite.code.toCharArray().joinToString(" "))
    val copyLabel = stringResource(R.string.invite_copy)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.heroGradient),
    ) {
        HeroWaves(Modifier.matchParentSize())
        Column(Modifier.padding(start = Spacing.xxl, end = Spacing.m, top = Spacing.m, bottom = Spacing.xxlPlus)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.invite_code_label),
                    style = HeroLabelStyle,
                    color = colors.onHeroMuted,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copyLabel, invite.code)))
                        }
                    },
                    modifier = Modifier
                        .size(Dimens.TouchTarget)
                        .background(colors.heroOverlay, CircleShape),
                ) {
                    Icon(painterResource(R.drawable.ic_content_copy), contentDescription = copyLabel, tint = colors.onHero)
                }
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = shown,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onHero,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = Spacing.m)
                    .clearAndSetSemantics { contentDescription = spoken },
            )
        }
    }
}

@Composable
private fun InfoPill(icon: Int, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)
            .padding(horizontal = Spacing.mPlus, vertical = Spacing.s),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.s),
        )
        Spacer(Modifier.width(Spacing.s))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The Android share sheet with the invite message. */
private fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title))
}

/** The code card's place while the code is created. */
private val CodeLoadingMinHeight = 160.dp
