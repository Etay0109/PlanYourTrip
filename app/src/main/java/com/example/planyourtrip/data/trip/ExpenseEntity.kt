package com.example.planyourtrip.data.trip

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    /** Whole agorot (1/100 shekel). */
    @ColumnInfo(name = "amount_agorot") val amountAgorot: Long,
    /** Stored as the enum name (see [Converters]). */
    val category: ExpenseCategory,
    /** Stored as epoch day (see [Converters]). */
    val date: LocalDate,
)

fun ExpenseEntity.toExpense() = Expense(
    id = id,
    tripId = tripId,
    name = name,
    amountAgorot = amountAgorot,
    category = category,
    date = date,
)
