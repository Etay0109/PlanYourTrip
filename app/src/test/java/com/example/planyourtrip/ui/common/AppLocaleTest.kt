package com.example.planyourtrip.ui.common

import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.ui.tripdetails.CurrencyConversion
import com.example.planyourtrip.ui.tripdetails.expense.ExpenseCurrency
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Locale

/** Every formatter's default output is English, whatever the device's (JVM default) locale is. */
@RunWith(Parameterized::class)
class AppLocaleTest(private val deviceLocale: Locale) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales() = listOf(
            Locale.forLanguageTag("he-IL"),
            Locale.forLanguageTag("ar-EG"),
            Locale.forLanguageTag("fa-IR"),
            Locale.GERMANY,
        )
    }

    private lateinit var saved: Locale
    private val nov12 = LocalDate.of(2026, 11, 12)

    @Before
    fun setDeviceLocale() {
        saved = Locale.getDefault()
        Locale.setDefault(deviceLocale)
    }

    @After
    fun restore() {
        Locale.setDefault(saved)
    }

    @Test
    fun appLocale_isEnglish() {
        assertEquals("en", AppLocale.locale.language)
    }

    @Test
    fun dates_areEnglish() {
        assertEquals("12–17 Nov 2026", TripFormatting.dateRange(nov12, nov12.plusDays(5)))
        assertEquals("12 Nov 2026", TripFormatting.date(nov12))
        assertEquals("12 Nov", TripFormatting.dayMonth(nov12))
        assertEquals("Thu 12 Nov", TripFormatting.weekdayDayMonth(nov12))
        assertEquals("09:05", TripFormatting.time(LocalTime.of(9, 5)))
        assertEquals("Nov 2026", TripFormatting.monthYear(nov12))
        assertEquals("Nov 12, 2026", TripFormatting.fieldDate(nov12))
        val oct9At1607 = LocalDateTime.of(2026, 10, 9, 16, 7).atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals("9 Oct, 16:07", TripFormatting.dayMonthTime(oct9At1607, ZoneOffset.UTC))
    }

    @Test
    fun numbers_areEnglish() {
        assertEquals("2,550", Money.formatAgorot(255_000))
        assertEquals("1,234.50", Money.formatAgorot(123_450))
        assertEquals("57.5%", Money.formatPercent(57.5))
        assertEquals("1,234.57", CurrencyConversion.formatRate(BigDecimal("1234.5678")))
    }

    @Test
    fun currencies_areEnglish() {
        assertEquals("€", CurrencyConversion.currencySymbol("EUR"))
        assertEquals("Euro", currencyName("EUR", emptyList()))
        val original = ForeignAmount(BigDecimal("1500"), "JPY", BigDecimal.ONE, nov12)
        assertEquals("¥1,500", ExpenseCurrency.formatOriginal(original))
        assertEquals(
            "€10.00",
            ExpenseCurrency.formatOriginal(ForeignAmount(BigDecimal("10.00"), "EUR", BigDecimal("4"), nov12)),
        )
    }
}
