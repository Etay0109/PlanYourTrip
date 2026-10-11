package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.ui.common.currencyFlag

/**
 * A flag emoji ([com.example.planyourtrip.ui.common.countryFlag] / `currencyFlag`) as its own text, next to the name
 * or code it belongs to (so it never changes the direction or the ellipsis of user text). Decorative: TalkBack reads
 * the name or code instead.
 */
@Composable
fun FlagEmoji(flag: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    Text(text = flag, style = style, maxLines = 1, modifier = modifier.clearAndSetSemantics {})
}

/** A currency code with its flag before it ("🇪🇺 EUR"); just the code when the currency has no flag. */
@Composable
fun CurrencyCodeLabel(code: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        currencyFlag(code)?.let {
            FlagEmoji(it, style = style)
            Spacer(Modifier.width(4.dp))
        }
        Text(code, style = style, color = color, maxLines = 1)
    }
}
