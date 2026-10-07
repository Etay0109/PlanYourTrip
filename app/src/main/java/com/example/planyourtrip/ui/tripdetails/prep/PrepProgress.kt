package com.example.planyourtrip.ui.tripdetails.prep

import com.example.planyourtrip.data.trip.ChecklistItem
import com.example.planyourtrip.data.trip.ChecklistKind
import java.time.LocalDate

/** Done/total of both checklists together (the Prep progress card). */
data class PrepProgress(val done: Int, val total: Int) {
    val left: Int get() = total - done

    /** Whole percent, rounded **down**, so 19 of 20 shows 95% and 100% only means everything is done. */
    val percent: Int get() = if (total == 0) 0 else done * 100 / total

    /** Filled part of the progress bar, 0..1. */
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

/** Done/total of one list ("Before the trip (5/8)"). */
data class ListCount(val done: Int, val total: Int)

/** The line under the progress bar. */
sealed interface ProgressMessage {
    /** No item in either list yet: invites the user to add the first one. */
    data object Empty : ProgressMessage

    data object AllDone : ProgressMessage

    /** "N things left before your trip" (the trip hasn't started). */
    data class LeftBeforeTrip(val count: Int) : ProgressMessage

    /** "N things left" (the trip has started or ended). */
    data class Left(val count: Int) : ProgressMessage
}

/** How an item's due date is shown. */
enum class DueState {
    /** No due date. */
    NONE,

    /** Due today or later, or already done: gray. */
    UPCOMING,

    /** Not done and the due date has passed: the warning color. */
    OVERDUE,
}

fun prepProgress(items: List<ChecklistItem>): PrepProgress =
    PrepProgress(done = items.count { it.checked }, total = items.size)

fun listCount(items: List<ChecklistItem>, kind: ChecklistKind): ListCount {
    val ofKind = items.filter { it.kind == kind }
    return ListCount(done = ofKind.count { it.checked }, total = ofKind.size)
}

fun progressMessage(progress: PrepProgress, tripStart: LocalDate, today: LocalDate): ProgressMessage = when {
    progress.total == 0 -> ProgressMessage.Empty
    progress.left == 0 -> ProgressMessage.AllDone
    today < tripStart -> ProgressMessage.LeftBeforeTrip(progress.left)
    else -> ProgressMessage.Left(progress.left)
}

/** Overdue = not done and due before [today]; an item due today is not overdue yet. */
fun dueState(item: ChecklistItem, today: LocalDate): DueState {
    val due = item.dueDate ?: return DueState.NONE
    return if (!item.checked && due < today) DueState.OVERDUE else DueState.UPCOMING
}

/** The items of one list in the order they were added (`createdAt`, then id). */
fun itemsOf(items: List<ChecklistItem>, kind: ChecklistKind): List<ChecklistItem> =
    items.filter { it.kind == kind }.sortedWith(compareBy({ it.createdAt }, { it.id }))

/**
 * The suggested items to add to [kind]: all of [suggestions] while that list is empty, nothing otherwise, so
 * suggestions are never added twice or mixed into a list the user started.
 */
fun suggestionsToAdd(items: List<ChecklistItem>, kind: ChecklistKind, suggestions: List<String>): List<String> =
    if (items.any { it.kind == kind }) emptyList() else suggestions.map { it.trim() }.filter { it.isNotEmpty() }
