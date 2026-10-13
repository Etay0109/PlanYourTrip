package com.example.planyourtrip.ui.tripform

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.planyourtrip.R
import com.example.planyourtrip.data.place.PlaceSuggestion
import com.example.planyourtrip.data.place.RegionKind
import com.example.planyourtrip.ui.common.components.FlagEmoji
import com.example.planyourtrip.ui.common.components.Pill
import com.example.planyourtrip.ui.common.countryFlag
import com.example.planyourtrip.ui.theme.Dimens

/**
 * The destination [field] with the search bar and the suggestion list drawn **under** it, over the fields that
 * follow: the layout reports the field's height only, so typing never moves the dates, currency or Save button.
 * The thin bar sits in the gap to the next field while [isSearching]; TalkBack hears "Searching" once when it starts
 * (a live region). [onOverlayHeight] gets the height drawn below the field (bar gap + list, in px), so the form can
 * keep room to scroll the whole list into view.
 */
@Composable
fun DestinationFieldWithSuggestions(
    isSearching: Boolean,
    suggestions: List<PlaceSuggestion>,
    onPick: (PlaceSuggestion) -> Unit,
    onOverlayHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
    field: @Composable () -> Unit,
) {
    val searching = stringResource(R.string.place_search_in_progress)
    Layout(
        contents = listOf(field) + {
            Column(Modifier.onSizeChanged { onOverlayHeight(it.height) }) {
                Spacer(Modifier.height(SearchBarGap))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Dimens.SearchBarHeight)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                            if (isSearching) contentDescription = searching
                        },
                ) {
                    if (isSearching) {
                        LinearProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Dimens.SearchBarHeight),
                        )
                    }
                }
                if (suggestions.isNotEmpty()) {
                    // The list starts where the next field's gap ends, as it did inside the column.
                    Spacer(Modifier.height(SearchBarGap))
                    DestinationSuggestionList(suggestions, onPick)
                }
            }
        },
        // Drawn (and touched) above the fields that follow it in the column.
        modifier = modifier.zIndex(1f),
    ) { (fieldMeasurables, overlayMeasurables), constraints ->
        val fieldPlaceable = fieldMeasurables.single().measure(constraints)
        val width = fieldPlaceable.width
        val overlay = overlayMeasurables.single()
            .measure(Constraints(minWidth = width, maxWidth = width))
        layout(width, fieldPlaceable.height) {
            fieldPlaceable.place(0, 0)
            overlay.place(0, fieldPlaceable.height)
        }
    }
}

/** Space above and below the search bar: together with the bar, the form's 8dp gap between fields. */
private val SearchBarGap = 2.5.dp

/**
 * The places matching the typed destination, under the field (inside the form's scrolling column, so the keyboard
 * never covers it): flag, name, region and country. A province, state, region or county gets a small gray label
 * ("Province"), so it never looks like the city of the same name. Ends with the OpenStreetMap attribution its data
 * license asks for.
 */
@Composable
fun DestinationSuggestionList(suggestions: List<PlaceSuggestion>, onPick: (PlaceSuggestion) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            suggestions.forEachIndexed { index, suggestion ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SuggestionRow(suggestion, onPick)
            }
            Text(
                text = stringResource(R.string.place_suggestions_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: PlaceSuggestion, onPick: (PlaceSuggestion) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget + 8.dp)
            .clickable(role = Role.Button) { onPick(suggestion) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // A fixed slot, so names line up also next to a place without a country.
        Box(Modifier.width(Dimens.SuggestionFlagWidth), contentAlignment = Alignment.CenterStart) {
            val flag = countryFlag(suggestion.countryCode)
            if (flag != null) {
                FlagEmoji(flag, style = MaterialTheme.typography.titleLarge)
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_location),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = suggestion.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                suggestion.regionKind?.let { kind ->
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        text = stringResource(kind.labelRes()),
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            suggestion.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun RegionKind.labelRes(): Int = when (this) {
    RegionKind.PROVINCE -> R.string.place_kind_province
    RegionKind.STATE -> R.string.place_kind_state
    RegionKind.REGION -> R.string.place_kind_region
    RegionKind.COUNTY -> R.string.place_kind_county
}
