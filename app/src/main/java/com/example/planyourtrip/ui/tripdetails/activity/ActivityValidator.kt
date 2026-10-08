package com.example.planyourtrip.ui.tripdetails.activity

import com.example.planyourtrip.ui.common.Money

/** Validation error codes for the activity form, mapped to string resources in the UI. */
enum class ActivityFieldError {
    EMPTY_TITLE,
    /** Cost entered but not a number, more than two decimals, too large, or not greater than zero. */
    INVALID_COST,
}

/** Pure validation rules (unit-tested). Each function returns null when valid. */
object ActivityValidator {
    fun validateTitle(title: String): ActivityFieldError? =
        if (title.isBlank()) ActivityFieldError.EMPTY_TITLE else null

    /** The cost is optional: empty is fine, anything else must be an amount greater than zero. */
    fun validateCost(cost: String): ActivityFieldError? {
        if (cost.isBlank()) return null
        return if (Money.parsePositiveAgorot(cost) == null) ActivityFieldError.INVALID_COST else null
    }
}
