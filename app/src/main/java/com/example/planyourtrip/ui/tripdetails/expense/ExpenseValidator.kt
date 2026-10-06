package com.example.planyourtrip.ui.tripdetails.expense

/** Validation error codes for the expense form, mapped to string resources in the UI. */
enum class ExpenseFieldError {
    EMPTY_NAME,
    EMPTY_AMOUNT,
    /** Not a number, too many decimals for the currency, too large, or not greater than zero. */
    INVALID_AMOUNT,
    /** A trip-currency amount while its rate is still being fetched. */
    NO_RATE,
}

/**
 * Pure validation rules (unit-tested). Each function returns null when valid. The amount is validated by
 * [ExpenseCurrency.resolveAmount], because its rules depend on the currency.
 */
object ExpenseValidator {
    fun validateName(name: String): ExpenseFieldError? =
        if (name.isBlank()) ExpenseFieldError.EMPTY_NAME else null
}
