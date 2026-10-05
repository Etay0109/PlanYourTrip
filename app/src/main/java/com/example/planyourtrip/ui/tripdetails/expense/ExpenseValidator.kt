package com.example.planyourtrip.ui.tripdetails.expense

import com.example.planyourtrip.ui.common.Money

/** Validation error codes for the expense form, mapped to string resources in the UI. */
enum class ExpenseFieldError {
    EMPTY_NAME,
    EMPTY_AMOUNT,
    /** Not a number, more than two decimals, too large, or not greater than zero. */
    INVALID_AMOUNT,
}

/** Pure validation rules (unit-tested). Each function returns null when valid. */
object ExpenseValidator {
    fun validateName(name: String): ExpenseFieldError? =
        if (name.isBlank()) ExpenseFieldError.EMPTY_NAME else null

    fun validateAmount(amount: String): ExpenseFieldError? {
        if (amount.isBlank()) return ExpenseFieldError.EMPTY_AMOUNT
        val agorot = Money.parseAgorot(amount)
        return if (agorot == null || agorot <= 0) ExpenseFieldError.INVALID_AMOUNT else null
    }
}
