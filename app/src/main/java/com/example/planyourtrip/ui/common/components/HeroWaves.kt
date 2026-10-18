package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme

/** Faint flowing lines decorating the teal hero gradient (home hero card, trip header), as in the design. */
@Composable
fun HeroWaves(modifier: Modifier = Modifier) {
    val waveColor = ExtendedTheme.colors.heroWave
    val glowColor = ExtendedTheme.colors.heroGlow
    Canvas(modifier) {
        val stroke = Stroke(width = Dimens.Hairline.toPx())
        val w = size.width
        val h = size.height
        listOf(0.18f, 0.42f, 0.66f, 0.9f).forEachIndexed { i, y ->
            val path = Path().apply {
                val startY = h * y
                moveTo(0f, startY)
                cubicTo(
                    w * 0.3f, startY - h * (0.22f - i * 0.03f),
                    w * 0.6f, startY + h * 0.25f,
                    w, startY - h * 0.18f,
                )
            }
            drawPath(path, waveColor, style = stroke)
        }
        drawCircle(
            color = glowColor,
            radius = h * 0.9f,
            center = Offset(w * 1.02f, h * 1.1f),
        )
    }
}
