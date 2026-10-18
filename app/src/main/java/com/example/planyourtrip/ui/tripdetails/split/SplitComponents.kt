package com.example.planyourtrip.ui.tripdetails.split

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.MergeBlock
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.common.split.MergeSuggestion
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.common.split.Transfer
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.tripdetails.components.TravelerAvatar
import com.example.planyourtrip.ui.tripdetails.components.personName

/** What the Budget tab's split sections can do; the dialogs and the sheet are in [SplitDialogs]. */
@Immutable
data class SplitActions(
    val onAddTraveler: () -> Unit,
    val onTravelerClick: (Traveler) -> Unit,
    val onSettleUp: () -> Unit,
    val onShowHistory: () -> Unit,
    /** "Merge" on the merge suggestion card. */
    val onMerge: () -> Unit = {},
    /** "Keep separate" / "OK" on the merge suggestion card. */
    val onKeepSeparate: () -> Unit = {},
) {
    companion object {
        val None = SplitActions({}, {}, {}, {})
    }
}

/**
 * "Travelers" + count pill, then a row of round avatars with the name under each ("You" first, not tappable) and a
 * dashed "+ Add" circle at the end. Scrolls sideways when there are many.
 */
@Composable
fun TravelersSection(
    state: SplitUiState.Loaded,
    actions: SplitActions,
    modifier: Modifier = Modifier,
) {
    val count = state.people.size
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = Dimens.ScreenPadding),
        ) {
            Text(
                text = stringResource(R.string.travelers_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.width(Spacing.sPlus))
            val countDescription = pluralStringResource(R.plurals.travelers_count_description, count, count)
            Pill(
                text = count.toString(),
                container = MaterialTheme.colorScheme.surfaceContainerHighest,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics { contentDescription = countDescription },
            )
        }
        Spacer(Modifier.height(Spacing.m))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Dimens.ScreenPadding - Spacing.xs),
        ) {
            state.people.forEach { person ->
                val traveler = state.travelers.firstOrNull { it.uuid == person.key }
                TravelerItem(person, onClick = traveler?.let { { actions.onTravelerClick(it) } })
            }
            AddTravelerItem(actions.onAddTraveler)
        }
    }
}

/**
 * One avatar with the name under it; [onClick] null = not tappable ("You"). A traveler whose name matches a member
 * gets a small "name only" label under the name, so the two are easy to tell apart.
 */
@Composable
private fun TravelerItem(person: Person, onClick: (() -> Unit)?) {
    val nameOnly = person is Person.Named && person.nameOnly
    val name = if (person is Person.Named) person.name else personName(person)
    val spoken = personName(person)
    val description = if (onClick != null) stringResource(R.string.traveler_edit_description, spoken) else spoken
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TravelerItemWidth)
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = Spacing.xs),
    ) {
        TravelerAvatar(person, Dimens.TravelerAvatarSize)
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (nameOnly) {
            Text(
                text = stringResource(R.string.traveler_name_only),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * "Noa joined this trip. Merge with the traveler 'Noa'?" with what merging does, "Keep separate" and "Merge"; when
 * merging would change a balance, why not and "OK". Shown once per traveler and member (see SplitViewModel).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MergeSuggestionCard(
    suggestion: MergeSuggestion,
    isMerging: Boolean,
    actions: SplitActions,
    modifier: Modifier = Modifier,
) {
    val traveler = suggestion.traveler.name
    val question = if (suggestion.isYou) {
        stringResource(R.string.merge_suggestion_you, traveler)
    } else {
        stringResource(R.string.merge_suggestion_member, suggestion.memberName, traveler)
    }
    val body = when (suggestion.blocked) {
        null -> if (suggestion.isYou) {
            stringResource(R.string.merge_suggestion_body_you, traveler)
        } else {
            stringResource(R.string.merge_suggestion_body_member, traveler, suggestion.memberName)
        }
        MergeBlock.SHARED_TOGETHER -> stringResource(R.string.merge_blocked_shared, traveler)
        MergeBlock.BALANCES_WOULD_CHANGE -> stringResource(R.string.merge_blocked_balances, traveler)
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = Dimens.CardElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xxl)) {
            Text(
                text = question,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xsPlus))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.m))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (suggestion.blocked != null) {
                    TextButton(onClick = actions.onKeepSeparate) { Text(stringResource(R.string.merge_ok)) }
                } else {
                    TextButton(onClick = actions.onKeepSeparate, enabled = !isMerging) {
                        Text(stringResource(R.string.merge_keep_separate))
                    }
                    FilledTonalButton(onClick = actions.onMerge, enabled = !isMerging) {
                        Text(stringResource(R.string.merge_action))
                    }
                }
            }
        }
    }
}

/** The dashed "+ Add" circle. */
@Composable
private fun AddTravelerItem(onClick: () -> Unit) {
    val description = stringResource(R.string.travelers_add_description)
    val outline = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(TravelerItemWidth)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = Spacing.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(Dimens.TravelerAvatarSize)
                .drawBehind {
                    val stroke = DashedStroke.toPx()
                    drawCircle(
                        color = outline,
                        radius = size.minDimension / 2 - stroke / 2,
                        center = Offset(size.width / 2, size.height / 2),
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())),
                        ),
                    )
                },
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = muted)
        }
        Spacer(Modifier.height(Spacing.s))
        Text(
            text = stringResource(R.string.travelers_add),
            style = MaterialTheme.typography.bodyMedium,
            color = muted,
            maxLines = 1,
        )
    }
}

/** Avatar + room for a short name under it. */
private val TravelerItemWidth = Dimens.TravelerAvatarSize + Spacing.l

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
            fun width(text: String, style: TextStyle) =
                measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
            with(density) {
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

/** The dashed "+ Add" circle. */
private val DashedStroke = 1.5.dp
private val DashOn = 5.dp
private val DashOff = 4.dp
