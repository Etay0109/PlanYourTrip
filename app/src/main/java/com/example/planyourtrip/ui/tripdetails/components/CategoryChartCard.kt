package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.CategoryTotal
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.HeroLabelStyle
import com.example.planyourtrip.ui.theme.isLargeFontScale

/**
 * "By category" card: donut chart of my spending per category (my share of each expense) with "TOTAL" in the middle,
 * and the legend next to it (under it when narrow or with large text). Categories that only have expenses I'm not
 * part of are listed as ₪0, 0% and get no slice.
 */
@Composable
fun CategoryChartCard(totals: List<CategoryTotal>, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.budget_by_category),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(20.dp))
            BoxWithConstraints {
                val stacked = maxWidth < Dimens.NarrowContentWidth || isLargeFontScale()
                if (stacked) {
                    Column {
                        Donut(totals, Modifier.align(Alignment.CenterHorizontally).size(160.dp))
                        Spacer(Modifier.height(20.dp))
                        Legend(totals)
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(totals, Modifier.size(140.dp))
                        Spacer(Modifier.width(20.dp))
                        Legend(totals, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(totals: List<CategoryTotal>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        totals.forEach { LegendRow(it) }
    }
}

@Composable
private fun Donut(totals: List<CategoryTotal>, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    val slices = totals.filter { it.amountAgorot > 0 }
    val sliceColors = slices.map { colors.category(it.category).content }
    val track = colors.budgetTrack
    val description = stringResource(R.string.budget_chart_description)
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 22.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            if (slices.isEmpty()) {
                // Nothing counted for me yet: an empty ring.
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(strokeWidth))
                return@Canvas
            }
            // A small gap between slices keeps neighbouring colors apart; none for a single slice.
            val gap = if (slices.size > 1) 2f else 0f
            var start = -90f
            slices.forEachIndexed { i, total ->
                val sweep = (total.percent * 3.6).toFloat()
                drawArc(
                    color = sliceColors[i],
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
            Text(
                text = stringResource(R.string.budget_chart_total),
                style = HeroLabelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = shekels(totals.sumOf { it.amountAgorot }),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LegendRow(total: CategoryTotal) {
    val color = ExtendedTheme.colors.category(total.category).content
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(total.category.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = shekels(total.amountAgorot),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = Money.formatPercent(total.percent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 32.dp),
        )
    }
}
