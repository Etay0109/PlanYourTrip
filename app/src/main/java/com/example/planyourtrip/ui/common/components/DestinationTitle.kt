package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.ui.common.countryFlag
import com.example.planyourtrip.ui.common.shortDestination

/**
 * A trip's destination with its country's flag before it (Home cards, trip header): "🇬🇧 London" for a picked
 * "London, United Kingdom" ([shortDestination]). Without a country code it is just the full text. The flag is its own
 * text, so a Hebrew destination keeps its direction and its ellipsis. TalkBack reads the full destination.
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
    Row(
        modifier.clearAndSetSemantics { contentDescription = destination },
        verticalAlignment = Alignment.Top,
    ) {
        countryFlag(countryCode)?.let {
            FlagEmoji(it, style = style)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = shortDestination(destination, countryCode),
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
