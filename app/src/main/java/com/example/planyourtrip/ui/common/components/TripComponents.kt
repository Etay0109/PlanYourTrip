package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.TripFormatting
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.minFitTextScale
import java.time.LocalDate

/** [Dimens.AvatarSize] rounded square with the destination's first letter. */
@Composable
fun LetterSquare(
    destination: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(Dimens.AvatarSize)
            .background(container, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = destination.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = content,
        )
    }
}

/** Small pill, e.g. "In 120 days" or "2 trips". */
@Composable
fun Pill(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
    }
}

/**
 * Calendar icon + the trip's dates and length on **one line**: "19–24 Oct · 6 days" (the year only when the trip
 * isn't in [currentYear]). When the whole line doesn't fit, the length is dropped first ("19–24 Oct"); when even the
 * dates don't fit (a tiny screen with a large font), their text gets smaller until it does. It never wraps.
 * TalkBack always reads the whole line.
 */
@Composable
fun TripDatesRow(
    start: LocalDate,
    end: LocalDate,
    currentYear: Int?,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val range = TripFormatting.dateRange(start, end, currentYear = currentYear)
    val length = TripFormatting.lengthInDays(start, end)
    val full = stringResource(
        R.string.home_dates_with_length,
        range,
        pluralStringResource(R.plurals.trip_length_days, length, length),
    )
    DatesRowLayout(color, modifier.clearAndSetSemantics { text = AnnotatedString(full) }) {
        // The row reads the whole line; the shown text (maybe shortened) is silent. It sits in a subcomposition, which
        // the row's clearAndSetSemantics doesn't reach.
        OneLineText(listOf(full, range), color, style, Modifier.clearAndSetSemantics {})
    }
}

/** Calendar icon + a short date text on one line, e.g. "Aug 2026" on a past trip's card. */
@Composable
fun TripDatesRow(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    DatesRowLayout(color, modifier) { OneLineText(listOf(text), color, style) }
}

@Composable
private fun DatesRowLayout(color: Color, modifier: Modifier, text: @Composable () -> Unit) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_calendar),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        text()
    }
}

/**
 * The first of [candidates] (longest first) that fits the width on one line; when none does, the last one in a
 * smaller size (down to [minFitTextScale]), cut with an ellipsis only beyond that. It takes only the width it needs,
 * so a photo's scrim still ends where the text ends.
 */
@Composable
private fun OneLineText(
    candidates: List<String>,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints {
        val measurer = rememberTextMeasurer()
        val maxWidth = constraints.maxWidth
        val minScale = minFitTextScale(LocalDensity.current.fontScale)
        val (text, textStyle) = remember(candidates, style, maxWidth, measurer, minScale) {
            fun fits(text: String, textStyle: TextStyle) =
                measurer.measure(text, textStyle, maxLines = 1, softWrap = false).size.width <= maxWidth
            candidates.firstOrNull { fits(it, style) }?.let { it to style }
                ?: run {
                    val last = candidates.last()
                    var scale = 1f
                    var smaller = style
                    while (scale > minScale && !fits(last, smaller)) {
                        scale = (scale - 0.05f).coerceAtLeast(minScale)
                        smaller = style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight)
                    }
                    last to smaller
                }
        }
        Text(
            text = text,
            style = textStyle,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}
