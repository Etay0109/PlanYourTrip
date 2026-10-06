package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.LocalDate

/** An expense of a trip. Deleting the trip deletes its expenses (foreign key CASCADE). */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["trip_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trip_id")],
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "trip_id") val tripId: Long,
    val name: String,
    /** Whole agorot (1/100 shekel): the shekel amount the budget uses, also for foreign-currency expenses. */
    @ColumnInfo(name = "amount_agorot") val amountAgorot: Long,
    /** Stored as the enum name (see [Converters]). */
    val category: ExpenseCategory,
    /** Stored as epoch day (see [Converters]). */
    val date: LocalDate,
    /** Foreign-currency columns: all four are set together, or all null (an expense entered in shekels). */
    @ColumnInfo(name = "original_amount") val originalAmount: BigDecimal? = null,
    @ColumnInfo(name = "original_currency") val originalCurrency: String? = null,
    /** Shekels per 1 unit, exact text (see [Converters]). */
    @ColumnInfo(name = "exchange_rate") val exchangeRate: BigDecimal? = null,
    @ColumnInfo(name = "rate_date") val rateDate: LocalDate? = null,
)

fun ExpenseEntity.toExpense() = Expense(
    id = id,
    tripId = tripId,
    name = name,
    amountAgorot = amountAgorot,
    category = category,
    date = date,
    original = if (originalAmount != null && originalCurrency != null && exchangeRate != null && rateDate != null) {
        ForeignAmount(originalAmount, originalCurrency, exchangeRate, rateDate)
    } else {
        null
    },
)
