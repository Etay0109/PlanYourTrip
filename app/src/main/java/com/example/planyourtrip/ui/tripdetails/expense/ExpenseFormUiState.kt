package com.example.planyourtrip.ui.tripdetails.expense

import com.example.planyourtrip.data.trip.ExpenseCategory
import java.time.LocalDate

data class ExpenseFormUiState(
    /** True when changing an existing expense (shows "Edit expense" and the delete action). */
    val isEditing: Boolean = false,
    /** While editing, true until the expense's values are loaded into the fields. */
    val isLoading: Boolean = false,
    val name: String = "",
    /** Amount in shekels as typed, e.g. "120.50". Parsed to agorot on save. */
    val amount: String = "",
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val date: LocalDate,
    val nameError: ExpenseFieldError? = null,
    val amountError: ExpenseFieldError? = null,
    val isSaving: Boolean = false,
    /** Set once the expense is saved or deleted; the screen then navigates back. */
    val done: Boolean = false,
)
