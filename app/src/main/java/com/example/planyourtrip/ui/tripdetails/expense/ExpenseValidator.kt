package com.example.planyourtrip.ui.tripdetails.expense

import java.time.LocalDate

/** Validation error codes for the expense form, mapped to string resources in the UI. */
enum class ExpenseFieldError {
    EMPTY_NAME,
    EMPTY_AMOUNT,
    /** Not a number, too many decimals for the currency, too large, or not greater than zero. */
    INVALID_AMOUNT,
    /** A trip-currency amount while its rate is still being fetched. */
    NO_RATE,
    /** The date is after the trip's last day. */
    DATE_AFTER_TRIP,
    /** Nobody is selected in "Split between". */
    SPLIT_EMPTY,
}

/**
 * Pure validation rules (unit-tested). Each function returns null when valid. The amount is validated by
 * [ExpenseCurrency.resolveAmount], because its rules depend on the currency.
 */
object ExpenseValidator {
    fun validateName(name: String): ExpenseFieldError? =
        if (name.isBlank()) ExpenseFieldError.EMPTY_NAME else null

    /** No minimum: flights and hotels are often paid before the trip starts. Null [tripEnd] = no limit. */
    fun validateDate(date: LocalDate, tripEnd: LocalDate?): ExpenseFieldError? =
        if (tripEnd != null && date > tripEnd) ExpenseFieldError.DATE_AFTER_TRIP else null

    fun validateSplit(splitBetween: List<String>): ExpenseFieldError? =
        if (splitBetween.isEmpty()) ExpenseFieldError.SPLIT_EMPTY else null

    /** A new expense is dated today, or the trip's last day when the trip has already ended. */
    fun defaultDate(today: LocalDate, tripEnd: LocalDate?): LocalDate =
        if (tripEnd != null && today > tripEnd) tripEnd else today
}
