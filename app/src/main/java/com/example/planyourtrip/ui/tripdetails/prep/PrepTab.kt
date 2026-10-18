package com.example.planyourtrip.ui.tripdetails.prep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.Spacing
import java.time.LocalDate

/** What the Prep tab can do; the screen wires these to [PrepViewModel] (previews use [None]). */
data class PrepActions(
    /** Returns true when the item was added (the field is cleared for the next one). */
    val onAddItem: (ChecklistKind, String) -> Boolean,
    val onToggle: (Long) -> Unit,
    val onEditItemText: (Long) -> Unit,
    /** Sets or (null) clears a due date. */
    val onSetDueDate: (Long, LocalDate?) -> Unit,
    val onDeleteItem: (Long) -> Unit,
    val onAddSuggested: (ChecklistKind) -> Unit,
    /** Opens the system file picker (PDFs and images). */
    val onAddDocument: () -> Unit,
    val onViewDocument: (Long) -> Unit,
    val onEditDocument: (Long) -> Unit,
    val onDeleteDocument: (Long) -> Unit,
) {
    companion object {
        val None = PrepActions(
            onAddItem = { _, _ -> false },
            onToggle = {},
            onEditItemText = {},
            onSetDueDate = { _, _ -> },
            onDeleteItem = {},
            onAddSuggested = {},
            onAddDocument = {},
            onViewDocument = {},
            onEditDocument = {},
            onDeleteDocument = {},
        )
    }
}

/**
 * The Prep tab below the trip header (reference: design/trip_prep.png): progress over both checklists, documents
 * (this device only) and the checklist. The header already shows the destination and dates, so there is no title. Nothing is drawn until the data has loaded.
 */
fun LazyListScope.prepTab(state: PrepUiState, actions: PrepActions) {
    if (state !is PrepUiState.Loaded) return
    item(key = "prep_progress") {
        PrepProgressCard(
            state.progress, state.message, Modifier.padding(horizontal = Dimens.ScreenPadding).padding(top = Spacing.s),
        )
    }
    item(key = "prep_documents") { DocumentsSection(state.documents, actions) }
    item(key = "prep_checklist") { ChecklistSection(state, actions) }
}

/** "12 of 20 done", the percent pill, the bar and what is left; an invitation while both lists are empty. */
@Composable
fun PrepProgressCard(progress: PrepProgress, message: ProgressMessage, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = Dimens.CardElevation,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.xxl)) {
            if (message == ProgressMessage.Empty) {
                Text(
                    text = stringResource(R.string.prep_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xsPlus))
                Text(
                    text = stringResource(R.string.prep_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.prep_progress_done, progress.done, progress.total),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.m))
                Pill(
                    text = stringResource(R.string.prep_progress_percent, progress.percent),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(Spacing.xl))
            LinearProgressIndicator(
                progress = { progress.fraction },
                color = MaterialTheme.colorScheme.primary,
                trackColor = ExtendedTheme.colors.budgetTrack,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().height(Dimens.ProgressBarHeight),
            )
            Spacer(Modifier.height(Spacing.mPlus))
            Text(
                text = when (message) {
                    ProgressMessage.AllDone -> stringResource(R.string.prep_all_done)
                    is ProgressMessage.LeftBeforeTrip ->
                        pluralStringResource(R.plurals.prep_left_before_trip, message.count, message.count)
                    is ProgressMessage.Left -> pluralStringResource(R.plurals.prep_left, message.count, message.count)
                    ProgressMessage.Empty -> ""
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A section title ("Documents", "Checklist") with optional trailing content (count pill, "+ Add"). */
@Composable
internal fun PrepSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sPlus),
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        trailing()
    }
}
