package com.example.planyourtrip.data.trip

import java.time.LocalDate

/** Domain model of one trip expense. Money is in whole agorot (1/100 shekel), never floating point. */
data class Expense(
    val id: Long,
    val tripId: Long,
    val name: String,
    val amountAgorot: Long,
    val category: ExpenseCategory,
    val date: LocalDate,
)
