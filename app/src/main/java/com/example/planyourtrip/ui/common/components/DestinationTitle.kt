package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.isSpecified
import com.example.planyourtrip.ui.common.TitleFit
import com.example.planyourtrip.ui.common.countryFlag
import com.example.planyourtrip.ui.common.fitDestinationTitle
import com.example.planyourtrip.ui.common.shortDestination
import com.example.planyourtrip.ui.theme.DestinationTitleMinShare
import com.example.planyourtrip.ui.theme.Spacing
import com.example.planyourtrip.ui.theme.minFitTextScale

/**
 * A trip's destination with its country's flag before it (Home cards, trip header): "🇬🇧 London" for a picked
 * "London, United Kingdom" ([shortDestination]). Without a country code it is just the full text. The flag is its own
 * text, so a Hebrew destination keeps its direction and its ellipsis. TalkBack reads the full destination.
 *
 * It never breaks inside a word ([fitDestinationTitle]): the name (and its flag) first shrinks a little to stay on
 * one line, down to [destinationMinScale]; then it wraps between words up to [maxLines]; a single word that is too
 * long even then stays on one line and ends with "…".
 */
@Composable
fun DestinationTitle(
    destination: String,
    countryCode: String?,
    style: TextStyle,
    color: Color,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val text = shortDestination(destination, countryCode)
    val flag = countryFlag(countryCode)
    val minScale = destinationMinScale()
    var layout by remember { mutableStateOf<DestinationTitleLayout?>(null) }
    BoxWithConstraints(
        modifier.clearAndSetSemantics {
            contentDescription = destination
            layout?.let { this[DestinationTitleLayoutKey] = it }
        },
    ) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxWidth = constraints.maxWidth
        val fit = remember(text, flag, style, maxWidth, minScale, measurer, density) {
            if (!constraints.hasBoundedWidth) {
                TitleFit(1f, singleLine = false)
            } else {
                val measure = TitleMeasure(measurer, density, flag, style)
                fitDestinationTitle(
                    text, minScale, widthOf = measure::textWidth,
                    roomAt = { scale -> maxWidth - measure.flagRoom(scale) },
                )
            }
        }
        val scaled = style.scaledBy(fit.scale)
        Row(verticalAlignment = Alignment.Top) {
            flag?.let {
                FlagEmoji(it, style = scaled)
                Spacer(Modifier.width(FlagGap))
            }
            Text(
                text = text,
                style = scaled.copy(hyphens = Hyphens.None),
                color = color,
                maxLines = if (fit.singleLine) 1 else maxLines,
                softWrap = !fit.singleLine,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout = DestinationTitleLayout.of(text, it) },
                // The row reads the full destination; this text sits in a subcomposition, which the row's
                // clearAndSetSemantics doesn't reach.
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clearAndSetSemantics {},
            )
        }
    }
}

/** How small a destination title may get before it wraps (80% of its normal-font size, see [minFitTextScale]). */
@Composable
fun destinationMinScale(): Float = minFitTextScale(LocalDensity.current.fontScale, DestinationTitleMinShare)

/**
 * The title's width on one line, flag included, at [scale] of [style]: the Next trip card compares it with its room
 * to decide whether the countdown box must get narrower.
 */
fun destinationOneLineWidth(
    measurer: TextMeasurer,
    density: Density,
    destination: String,
    countryCode: String?,
    style: TextStyle,
    scale: Float,
): Int {
    val measure = TitleMeasure(measurer, density, countryFlag(countryCode), style)
    return measure.textWidth(shortDestination(destination, countryCode), scale) + measure.flagRoom(scale)
}

/** [style] at [scale] of its size (the line height too, so wrapped lines stay evenly spaced). */
internal fun TextStyle.scaledBy(scale: Float): TextStyle = if (scale >= 1f) {
    this
} else {
    copy(fontSize = fontSize * scale, lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight)
}

private class TitleMeasure(
    private val measurer: TextMeasurer,
    private val density: Density,
    private val flag: String?,
    private val style: TextStyle,
) {
    fun textWidth(text: String, scale: Float): Int =
        measurer.measure(text, style.scaledBy(scale), maxLines = 1, softWrap = false).size.width

    /** The flag and the gap after it; 0 without a flag. */
    fun flagRoom(scale: Float): Int =
        if (flag == null) 0 else textWidth(flag, scale) + with(density) { FlagGap.roundToPx() }
}

private val FlagGap = Spacing.s

/**
 * How the title's text was laid out, as a semantics property for the layout tests (TalkBack ignores it): its line
 * count, whether a line ended inside a word, and whether it ends with "…".
 */
data class DestinationTitleLayout(val lineCount: Int, val brokeInsideWord: Boolean, val ellipsized: Boolean) {
    companion object {
        fun of(text: String, result: TextLayoutResult): DestinationTitleLayout {
            val lines = result.lineCount
            val brokeInsideWord = (0 until lines - 1).any { line ->
                val end = result.getLineEnd(line)
                end in 1 until text.length && !text[end - 1].isWhitespace() && !text[end].isWhitespace()
            }
            return DestinationTitleLayout(lines, brokeInsideWord, result.isLineEllipsized(lines - 1))
        }
    }
}

val DestinationTitleLayoutKey = SemanticsPropertyKey<DestinationTitleLayout>("DestinationTitleLayout")
