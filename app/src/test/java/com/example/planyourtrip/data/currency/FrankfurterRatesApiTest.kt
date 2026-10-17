package com.example.planyourtrip.data.currency

import com.example.planyourtrip.data.http.RecordingHttpGet
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** The requests [FrankfurterRatesApi] makes (the answers' parsing is in FrankfurterJsonTest). */
class FrankfurterRatesApiTest {

    @Test
    fun latestRate_asksForTheQuoteAgainstShekels() = runTest {
        val http = RecordingHttpGet("""[{"date":"2026-10-12","base":"ILS","quote":"USD","rate":0.25}]""")

        val (rate, date) = FrankfurterRatesApi(http, "PlanYourTrip/1.0").latestToIls("USD")

        assertEquals(listOf("https://api.frankfurter.dev/v2/rates?base=ILS&quotes=USD"), http.urls)
        assertEquals(listOf("PlanYourTrip/1.0"), http.userAgents)
        assertEquals("4", rate.toPlainString())
        assertEquals(LocalDate.of(2026, 10, 12), date)
    }

    @Test
    fun currencies_asksForTheList() = runTest {
        val http = RecordingHttpGet("""[{"iso_code":"USD","name":"United States Dollar"}]""")

        val list = FrankfurterRatesApi(http, "ua").currencies()

        assertEquals(listOf("https://api.frankfurter.dev/v2/currencies"), http.urls)
        assertEquals(listOf(Currency("USD", "United States Dollar")), list)
    }
}
