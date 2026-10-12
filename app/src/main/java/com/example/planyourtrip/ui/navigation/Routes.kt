package com.example.planyourtrip.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe Navigation Compose destinations.

@Serializable
data object Login

@Serializable
data object Home

/** New trip when [tripId] is null; otherwise edits that trip. */
@Serializable
data class TripForm(val tripId: Long? = null)

/**
 * The trip area: header + Overview / Budget / Prep / Plan tabs. [initialTab] is a `TripTab` name to open on (a
 * tapped trip reminder opens Prep); null = Overview.
 */
@Serializable
data class TripDetails(val tripId: Long, val initialTab: String? = null)

/** New expense of [tripId] when [expenseId] is null; otherwise edits that expense. */
@Serializable
data class ExpenseForm(val tripId: Long, val expenseId: Long? = null)

/**
 * New activity of [tripId] when [activityId] is null (on the day [dateEpochDay], or the default day when null);
 * otherwise edits that activity.
 */
@Serializable
data class ActivityForm(val tripId: Long, val activityId: Long? = null, val dateEpochDay: Long? = null)

/**
 * The trip assistant chat of [tripId]. Its ViewModel belongs to the [TripDetails] entry below it, so the
 * conversation is kept while the trip screen is open.
 */
@Serializable
data class TripAssistant(val tripId: Long)
