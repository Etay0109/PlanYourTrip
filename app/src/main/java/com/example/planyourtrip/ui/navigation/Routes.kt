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

/** The trip area: header + Overview / Budget / Map / Plan tabs. */
@Serializable
data class TripDetails(val tripId: Long)

/** New expense of [tripId] when [expenseId] is null; otherwise edits that expense. */
@Serializable
data class ExpenseForm(val tripId: Long, val expenseId: Long? = null)
