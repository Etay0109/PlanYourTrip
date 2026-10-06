package com.example.planyourtrip.data.trip

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun tripColorToName(color: TripColor): String = color.name

    /** Unknown names (e.g. a removed palette entry) fall back to the first color instead of crashing. */
    @TypeConverter
    fun nameToTripColor(name: String): TripColor =
        TripColor.entries.firstOrNull { it.name == name } ?: TripColor.entries.first()

    @TypeConverter
    fun expenseCategoryToName(category: ExpenseCategory): String = category.name

    /** Unknown names fall back to [ExpenseCategory.OTHER] instead of crashing. */
    @TypeConverter
    fun nameToExpenseCategory(name: String): ExpenseCategory =
        ExpenseCategory.entries.firstOrNull { it.name == name } ?: ExpenseCategory.OTHER

    /** Exact decimal text (e.g. "3.4408"), so exchange rates never pass through floating point. */
    @TypeConverter
    fun bigDecimalToText(value: BigDecimal): String = value.toPlainString()

    @TypeConverter
    fun textToBigDecimal(text: String): BigDecimal = BigDecimal(text)

    @TypeConverter
    fun instantToEpochMilli(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun epochMilliToInstant(millis: Long): Instant = Instant.ofEpochMilli(millis)

    /** Minute of the day (0..1439); seconds are never stored. */
    @TypeConverter
    fun localTimeToMinute(time: LocalTime): Int = time.hour * 60 + time.minute

    @TypeConverter
    fun minuteToLocalTime(minute: Int): LocalTime = LocalTime.of(minute / 60, minute % 60)

    @TypeConverter
    fun activityTypeToName(type: ActivityType): String = type.name

    /** Unknown names fall back to [ActivityType.OTHER] instead of crashing. */
    @TypeConverter
    fun nameToActivityType(name: String): ActivityType =
        ActivityType.entries.firstOrNull { it.name == name } ?: ActivityType.OTHER
}
