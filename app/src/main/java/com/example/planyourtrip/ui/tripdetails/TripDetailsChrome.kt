package com.example.planyourtrip.ui.tripdetails

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.components.BackTopAppBar
import com.example.planyourtrip.ui.common.components.BrandMark
import com.example.planyourtrip.ui.common.iconRes
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.Spacing

/** The lazy key of the header, the first item on every tab (the status bar shade follows it). */
internal const val HEADER_KEY = "header"

/**
 * Fixed behind the status bar (it doesn't scroll): the screen's background color, fading out just below the bar.
 * Invisible while the list is at the top (the header's photo or gradient shows as before); it fades in over the first
 * [Dimens.TripStatusBarFade] of scrolling, so neither the header's buttons and title nor the cards run into the clock
 * and icons. The icons follow the system theme (dark in light mode, light in dark mode), so they are readable on the
 * background in both.
 */
@Composable
internal fun BoxScope.StatusBarShade(listState: LazyListState) {
    val density = LocalDensity.current
    val statusBarPx = WindowInsets.statusBars.getTop(density)
    if (statusBarPx == 0) return
    val fadePx = with(density) { Dimens.TripStatusBarFade.toPx() }
    val visibility by remember(listState, fadePx) {
        derivedStateOf {
            val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == HEADER_KEY }
            if (header == null) {
                1f
            } else {
                (-header.offset / fadePx).coerceIn(0f, 1f)
            }
        }
    }
    val color = MaterialTheme.colorScheme.background
    val statusBar = with(density) { statusBarPx.toDp() }
    val height = statusBar + Dimens.TripStatusBarFade
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(height)
            .graphicsLayer { alpha = visibility }
            .background(Brush.verticalGradient(0f to color, statusBar / height to color, 1f to Color.Transparent)),
    )
}

/** A Material floating action button. */
private val FabSize = 56.dp

/**
 * Bottom list padding on every tab: the buttons' height (56dp, the round sparkle and the "+ Add expense" pill alike),
 * the Scaffold's 16dp gap above the navigation bar, and 24dp of air, so the last card can scroll fully above them.
 */
internal val FabClearance = FabSize + Spacing.l + Spacing.xxl

/** Round button with a sparkle that opens the trip assistant. */
@Composable
internal fun AssistantFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.secondary,
        contentColor = MaterialTheme.colorScheme.onSecondary,
    ) {
        Icon(painterResource(R.drawable.ic_sparkle), contentDescription = stringResource(R.string.assistant_open))
    }
}

@Composable
internal fun TripNavigationBar(selected: TripTab, onSelect: (TripTab) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        TripTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.iconRes()), contentDescription = null) },
                label = { Text(stringResource(tab.labelRes()), maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

@DrawableRes
private fun TripTab.iconRes(): Int = when (this) {
    TripTab.OVERVIEW -> R.drawable.ic_dashboard
    TripTab.BUDGET -> R.drawable.ic_payments
    TripTab.PREP -> R.drawable.ic_checklist
    TripTab.PLAN -> R.drawable.ic_calendar
}

@StringRes
private fun TripTab.labelRes(): Int = when (this) {
    TripTab.OVERVIEW -> R.string.trip_tab_overview
    TripTab.BUDGET -> R.string.trip_tab_budget
    TripTab.PREP -> R.string.trip_tab_prep
    TripTab.PLAN -> R.string.trip_tab_plan
}

@Composable
internal fun NotFound(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackTopAppBar(onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(Dimens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(NotFoundTopGap))
            BrandMark(size = Dimens.EmptyStateMarkSize, iconSize = Dimens.EmptyStateMarkIconSize)
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                text = stringResource(R.string.trip_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** "Trip not found": space above the brand mark. */
private val NotFoundTopGap = 48.dp
