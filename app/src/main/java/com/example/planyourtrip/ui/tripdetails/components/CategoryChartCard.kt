package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.CategoryTotal
import com.example.planyourtrip.ui.common.Money
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.common.shekels
import com.example.planyourtrip.ui.theme.ExtendedTheme

/** "By category" card: donut chart of spending per category and a legend. Only categories with spending. */
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
            Donut(
                totals = totals,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(168.dp),
            )
            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                totals.forEach { LegendRow(it) }
            }
        }
    }
}

@Composable
private fun Donut(totals: List<CategoryTotal>, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    val sliceColors = totals.map { colors.category(it.category).content }
    val description = stringResource(R.string.budget_chart_description)
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 26.dp.toPx()
            val inset = strokeWidth / 2
            // A small gap between slices keeps neighbouring colors apart; none for a single slice.
            val gap = if (totals.size > 1) 2f else 0f
            var start = -90f
            totals.forEachIndexed { i, total ->
                val sweep = (total.percent * 3.6).toFloat()
                drawArc(
                    color = sliceColors[i],
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth),
                )
                start += sweep
            }
        }
        Text(
            text = shekels(totals.sumOf { it.amountAgorot }),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun LegendRow(total: CategoryTotal) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(total.category)
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(total.category.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = shekels(total.amountAgorot),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = Money.formatPercent(total.percent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
