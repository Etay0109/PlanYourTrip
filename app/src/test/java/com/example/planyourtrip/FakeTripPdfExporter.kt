package com.example.planyourtrip

import com.example.planyourtrip.ui.common.share.SharedPdf
import com.example.planyourtrip.ui.common.share.TripPdfContent
import com.example.planyourtrip.ui.common.share.TripPdfExporter
import kotlinx.coroutines.CompletableDeferred

/**
 * Records every export instead of drawing a PDF. Set [fail] to make exports throw; set [gate] to hold the next
 * export until it is completed (to see the "creating" state).
 */
class FakeTripPdfExporter : TripPdfExporter {
    val exports = mutableListOf<Pair<TripPdfContent, String>>()
    var fail = false
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun export(content: TripPdfContent, fileName: String): SharedPdf {
        gate?.await()
        exports += content to fileName
        if (fail) throw IllegalStateException("export failed")
        return SharedPdf("content://test/$fileName")
    }
}
