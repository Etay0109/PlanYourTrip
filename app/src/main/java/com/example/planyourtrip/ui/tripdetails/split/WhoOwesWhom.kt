package com.example.planyourtrip.ui.tripdetails.split

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.common.split.Transfer
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.components.TravelerAvatar
import com.example.planyourtrip.ui.tripdetails.components.personName

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Density

/**
 * "Who owes whom": one row per open transfer (two avatars with an arrow, the sentence, the amount) and "Settle up";
 * with no open debt, "All settled up" with a check (and "History" when payments were recorded).
 */
@Composable
fun WhoOwesWhomCard(
    state: SplitUiState.Loaded,
    actions: SplitActions,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = Dimens.CardElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xxl)) {
            Text(
                text = stringResource(R.string.owes_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            owesSummary(state.transfers, state.me)?.let {
                Spacer(Modifier.height(Spacing.xs))
                OwesSummaryLine(it)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = pluralStringResource(
                    R.plurals.owes_subtitle, state.sharedExpenseCount, state.sharedExpenseCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.l))
            if (state.transfers.isEmpty()) {
                SettledUp()
                if (state.settlements.isNotEmpty()) {
                    TextButton(onClick = actions.onShowHistory, modifier = Modifier.padding(top = Spacing.xs)) {
                        Text(stringResource(R.string.settle_history))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    state.transfers.forEach { TransferRow(it, state) }
                }
                Spacer(Modifier.height(Spacing.l))
                SettleUpButton(actions.onSettleUp)
            }
        }
    }
}

@Composable
private fun SettledUp() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
            .padding(Spacing.l),
    ) {
        Icon(
            painterResource(R.drawable.ic_check_circle),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Spacing.m))
        Text(
            text = stringResource(R.string.owes_settled),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** "You're owed ₪820.49" (teal), "You owe ₪150.00" (warning orange) or "You're all square". */
@Composable
private fun OwesSummaryLine(summary: OwesSummary) {
    val (text, color) = when (summary) {
        is OwesSummary.Owed ->
            stringResource(R.string.owes_summary_owed, shekels(summary.amountAgorot)) to
                MaterialTheme.colorScheme.primary
        is OwesSummary.Owe ->
            stringResource(R.string.owes_summary_owe, shekels(summary.amountAgorot)) to
                ExtendedTheme.colors.budgetWarning
        OwesSummary.AllSquare ->
            stringResource(R.string.owes_summary_square) to MaterialTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = color,
    )
}

/**
 * "Noa → You  Noa owes you  ₪320". Amounts owed to me are teal. The amount keeps its natural width on the right and
 * the sentence gets the rest. When the sentence wouldn't fit on one line there, it moves under the avatars and the
 * amount and gets the row's full width; when even the avatars and the amount don't fit side by side (narrow phone,
 * large font), the amount goes under the sentence. Only a long name wraps: the fixed words are joined by no-break
 * spaces.
 */
@Composable
fun TransferRow(transfer: Transfer, state: SplitUiState.Loaded, modifier: Modifier = Modifier) {
    val from = state.person(transfer.fromKey)
    val to = state.person(transfer.toKey)
    val sentence = transferSentence(from, to)
    val amount = shekels(transfer.amountAgorot)
    val description = stringResource(R.string.owes_row_description, sentence, amount)
    val sentenceStyle = MaterialTheme.typography.bodyLarge
    val amountStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val amountColor =
        if (to is Person.You) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = Spacing.m, vertical = Spacing.m),
    ) {
        val maxWidthPx = constraints.maxWidth
        val layout = remember(sentence, amount, sentenceStyle, amountStyle, maxWidthPx, density) {
            transferLayout(measurer, density, sentence, sentenceStyle, amount, amountStyle, maxWidthPx)
        }
        val sentenceText = @Composable { textModifier: Modifier ->
            Text(
                text = sentence,
                style = sentenceStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = textModifier,
            )
        }
        val amountText = @Composable {
            Text(text = amount, style = amountStyle, color = amountColor, maxLines = 1, softWrap = false)
        }
        when (layout) {
            TransferLayout.ONE_LINE -> Row(verticalAlignment = Alignment.CenterVertically) {
                TransferAvatars(from, to)
                Spacer(Modifier.width(TransferGap))
                sentenceText(Modifier.weight(1f))
                Spacer(Modifier.width(TransferGap))
                amountText()
            }
            TransferLayout.SENTENCE_BELOW -> Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TransferAvatars(from, to)
                    Spacer(Modifier.weight(1f).widthIn(min = TransferGap))
                    amountText()
                }
                Spacer(Modifier.height(Spacing.s))
                sentenceText(Modifier.fillMaxWidth())
            }
            TransferLayout.STACKED -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                TransferAvatars(from, to)
                sentenceText(Modifier.fillMaxWidth())
                amountText()
            }
        }
    }
}

private enum class TransferLayout { ONE_LINE, SENTENCE_BELOW, STACKED }

/** Which [TransferLayout] fits [maxWidthPx]: each text measured on one line. */
private fun transferLayout(
    measurer: TextMeasurer,
    density: Density,
    sentence: String,
    sentenceStyle: TextStyle,
    amount: String,
    amountStyle: TextStyle,
    maxWidthPx: Int,
): TransferLayout {
    fun width(text: String, style: TextStyle) =
        measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    return with(density) {
        val avatars = TransferAvatarsWidth.roundToPx()
        val amountWidth = width(amount, amountStyle)
        val free = maxWidthPx - avatars - TransferGap.roundToPx() * 2 - amountWidth
        when {
            width(sentence, sentenceStyle) <= free -> TransferLayout.ONE_LINE
            avatars + TransferGap.roundToPx() + amountWidth <= maxWidthPx -> TransferLayout.SENTENCE_BELOW
            else -> TransferLayout.STACKED
        }
    }
}

/** Between the avatars, the sentence and the amount. */
private val TransferGap = Spacing.s

/** [TransferAvatars]: two small avatars and the 16dp arrow with 4dp on each side. */
private val TransferAvatarsWidth = Dimens.TravelerAvatarSmallSize * 2 + Spacing.xxl

/** The payer's avatar, an arrow, the receiver's avatar. */
@Composable
fun TransferAvatars(from: Person, to: Person) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TravelerAvatar(from, Dimens.TravelerAvatarSmallSize)
        Icon(
            painterResource(R.drawable.ic_arrow_forward),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = Spacing.xs)
                .size(IconSize.xs),
        )
        TravelerAvatar(to, Dimens.TravelerAvatarSmallSize)
    }
}

/** "Noa owes you", "You owe Amit", "Amit owes Noa". */
@Composable
fun transferSentence(from: Person, to: Person): String = when {
    to is Person.You -> stringResource(R.string.owes_they_owe_you, personName(from))
    from is Person.You -> stringResource(R.string.owes_you_owe, personName(to))
    else -> stringResource(R.string.owes_they_owe, personName(from), personName(to))
}

/** "Noa paid you", "You paid Amit", "Amit paid Noa". */
@Composable
fun settlementSentence(settlement: Settlement, state: SplitUiState.Loaded): String {
    val from = state.person(settlement.fromKey)
    val to = state.person(settlement.toKey)
    return when {
        to is Person.You -> stringResource(R.string.settle_paid_you, personName(from))
        from is Person.You -> stringResource(R.string.settle_you_paid, personName(to))
        else -> stringResource(R.string.settle_paid, personName(from), personName(to))
    }
}

/** Tonal full-width "Settle up" button. */
@Composable
private fun SettleUpButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = Spacing.mPlus, horizontal = Spacing.l),
        ) {
            Icon(
                painterResource(R.drawable.ic_wallet),
                contentDescription = null,
                modifier = Modifier.size(IconSize.m),
            )
            Spacer(Modifier.width(Spacing.s))
            Text(stringResource(R.string.settle_up), style = MaterialTheme.typography.labelLarge)
        }
    }
}
