package com.example.planyourtrip.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import kotlin.math.roundToInt

private enum class SwipeValue { Closed, Revealed, Dismissed }

/**
 * Swipe a home trip card to the left to delete it. Behind the card is a red area with a trash icon and "Delete".
 * A short swipe springs back; past [Dimens.SwipeRevealWidth] the card stays open and the red area can be tapped; a
 * full swipe (or a fast fling) calls [onDelete] right away. [onDelete] opens the confirmation dialog: while it is
 * open ([deletePending]) the card stays where it is, and when it closes without deleting the card returns to its
 * place. Only horizontal drags are taken, so a swipe never opens the trip and a tap still does. With [leave] (a
 * shared trip someone else owns) the area reads "Leave" and [onDelete] asks to leave the trip instead.
 */
@Composable
fun SwipeToDeleteBox(
    shape: Shape,
    deletePending: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    leave: Boolean = false,
    content: @Composable () -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val state = remember { AnchoredDraggableState(SwipeValue.Closed) }
    val revealPx = with(LocalDensity.current) { Dimens.SwipeRevealWidth.toPx() }

    // A full swipe asks right away; the dialog decides what happens next.
    LaunchedEffect(state.settledValue) {
        if (state.settledValue == SwipeValue.Dismissed) currentOnDelete()
    }
    // Cancelled (or the delete failed): back to the start.
    LaunchedEffect(deletePending) {
        if (!deletePending && state.settledValue != SwipeValue.Closed) state.animateTo(SwipeValue.Closed)
    }

    val deleteLabel = stringResource(if (leave) R.string.trip_leave else R.string.trip_delete)
    Box(
        modifier
            .onSizeChanged { size ->
                state.updateAnchors(
                    DraggableAnchors {
                        SwipeValue.Closed at 0f
                        SwipeValue.Revealed at -revealPx
                        SwipeValue.Dismissed at -size.width.toFloat()
                    }
                )
            }
            .semantics {
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { currentOnDelete(); true })
            },
    ) {
        val offset = if (state.offset.isNaN()) 0f else state.offset
        if (offset < 0f) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(ExtendedTheme.colors.swipeDelete),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(Dimens.SwipeRevealWidth)
                        .fillMaxHeight()
                        .clickable(role = Role.Button, onClickLabel = deleteLabel) { currentOnDelete() },
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(if (leave) R.drawable.ic_logout else R.drawable.ic_delete),
                        contentDescription = null,
                        tint = ExtendedTheme.colors.onSwipeDelete,
                    )
                    Text(
                        text = stringResource(if (leave) R.string.action_leave else R.string.action_delete),
                        style = MaterialTheme.typography.labelLarge,
                        color = ExtendedTheme.colors.onSwipeDelete,
                    )
                }
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                        state = state,
                        positionalThreshold = { distance -> distance * 0.5f },
                    ),
                ),
        ) {
            content()
        }
    }
}
