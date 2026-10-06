package com.example.planyourtrip.data.trip

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Domain model of one trip expense. [amountAgorot] is the shekel amount in whole agorot (never floating point);
 * it is what the budget uses. [original] is set when the expense was entered in another currency.
 */
data class Expense(
    val id: Long,
    val tripId: Long,
    val name: String,
    val amountAgorot: Long,
    val category: ExpenseCategory,
    val date: LocalDate,
    val original: ForeignAmount? = null,
)

/**
 * What the user typed in a foreign currency and the rate used to convert it, fixed at save time:
 * `amountAgorot = amount × ilsPerUnit` rounded half-up to whole agorot.
 */
data class ForeignAmount(
    /** Exact amount as typed, e.g. 10.00 EUR or 1500 JPY. */
    val amount: BigDecimal,
    /** ISO 4217 code, e.g. "EUR". */
    val currencyCode: String,
    /** Shekels per 1 unit, as used for this expense. */
    val ilsPerUnit: BigDecimal,
    /** The date the rates service gave for [ilsPerUnit]. */
    val rateDate: LocalDate,
)
