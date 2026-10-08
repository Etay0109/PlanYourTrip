package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.data.trip.ME
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.TripColor
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Cloud document fields (pure, no Firebase types, so it is unit-tested on the JVM). Must match firestore.rules.
 * Money is whole agorot (Long), dates are ISO "2026-11-12", times "HH:mm", rates and foreign amounts exact
 * decimal text. Every live document writes every key (null when empty); a tombstone has only
 * [DELETED] = true and [UPDATED_AT].
 */
object SyncDocuments {
    const val UPDATED_AT = "updatedAt"
    const val DELETED = "deleted"

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun toDocument(item: SyncItem): Map<String, Any?> {
        val fields: Map<String, Any?> = when (val data = item.data) {
            null -> return mapOf(DELETED to true, UPDATED_AT to item.updatedAt)
            is ItemData.TripData -> mapOf(
                "destination" to data.destination,
                "startDate" to data.startDate.toString(),
                "endDate" to data.endDate.toString(),
                "budgetAgorot" to data.budgetAgorot,
                "currencyCode" to data.currencyCode,
                "color" to data.color.name,
            )
            is ItemData.ExpenseData -> mapOf(
                "name" to data.name,
                "amountAgorot" to data.amountAgorot,
                "category" to data.category.name,
                "date" to data.date.toString(),
                "originalAmount" to data.original?.amount?.toPlainString(),
                "originalCurrency" to data.original?.currencyCode,
                "exchangeRate" to data.original?.ilsPerUnit?.toPlainString(),
                "rateDate" to data.original?.rateDate?.toString(),
                "paidBy" to data.paidBy,
                "splitBetween" to data.splitBetween,
            )
            is ItemData.ActivityData -> with(data.input) {
                mapOf(
                    "date" to date.toString(),
                    "startTime" to startTime?.format(TIME),
                    "title" to title,
                    "note" to note,
                    "type" to type.name,
                    "location" to location,
                    "estimatedCostAgorot" to estimatedCostAgorot,
                    "booked" to booked,
                )
            }
            is ItemData.ChecklistData -> mapOf(
                "list" to data.list.name,
                "text" to data.text,
                "checked" to data.checked,
                "dueDate" to data.dueDate?.toString(),
                "createdAt" to data.createdAt,
            )
            is ItemData.TravelerData -> mapOf(
                "name" to data.name,
                "color" to data.color.name,
                "createdAt" to data.createdAt,
            )
            is ItemData.SettlementData -> mapOf(
                "fromTraveler" to data.fromKey,
                "toTraveler" to data.toKey,
                "amountAgorot" to data.amountAgorot,
                "date" to data.date.toString(),
                "createdAt" to data.createdAt,
            )
        }
        return fields + mapOf(DELETED to false, UPDATED_AT to item.updatedAt)
    }

    /**
     * Reads a cloud document; null when it can't be read (missing or wrong fields), so one bad document never
     * stops a sync. Unknown enum names fall back like in Room (first color, OTHER, BEFORE_TRIP). An expense without
     * `paidBy` / `splitBetween` (written before expense splitting) is "paid by me, not shared".
     */
    fun fromDocument(kind: SyncKind, uuid: String, tripUuid: String?, doc: Map<String, Any?>): SyncItem? =
        runCatching {
            val updatedAt = (doc[UPDATED_AT] as Number).toLong()
            val data = if (doc[DELETED] == true) null else when (kind) {
                SyncKind.TRIP -> ItemData.TripData(
                    destination = doc.string("destination")!!,
                    startDate = LocalDate.parse(doc.string("startDate")),
                    endDate = LocalDate.parse(doc.string("endDate")),
                    color = TripColor.entries.firstOrNull { it.name == doc["color"] } ?: TripColor.entries.first(),
                    budgetAgorot = doc.long("budgetAgorot"),
                    currencyCode = doc.string("currencyCode"),
                )
                SyncKind.EXPENSE -> {
                    // Documents written before expense splitting have no payer or split: paid by me, not shared.
                    val paidBy = doc.string("paidBy")?.takeIf { it.isNotEmpty() } ?: ME
                    val split = (doc["splitBetween"] as List<*>?).orEmpty()
                        .filterIsInstance<String>().filter { it.isNotEmpty() }.distinct()
                    ItemData.ExpenseData(
                        name = doc.string("name")!!,
                        amountAgorot = doc.long("amountAgorot")!!,
                        category = ExpenseCategory.entries.firstOrNull { it.name == doc["category"] }
                            ?: ExpenseCategory.OTHER,
                        date = LocalDate.parse(doc.string("date")),
                        original = foreignAmount(doc),
                        paidBy = paidBy,
                        splitBetween = split.ifEmpty { listOf(paidBy) },
                    )
                }
                SyncKind.ACTIVITY -> ItemData.ActivityData(
                    ActivityInput(
                        date = LocalDate.parse(doc.string("date")),
                        startTime = doc.string("startTime")?.let { LocalTime.parse(it, TIME) },
                        title = doc.string("title")!!,
                        type = ActivityType.entries.firstOrNull { it.name == doc["type"] } ?: ActivityType.OTHER,
                        note = doc.string("note"),
                        location = doc.string("location"),
                        estimatedCostAgorot = doc.long("estimatedCostAgorot"),
                        booked = doc["booked"] as Boolean,
                    ),
                    // Older documents may still hold "placeId" / "placeLookup" (removed with the Map tab): ignored.
                )
                SyncKind.CHECKLIST -> ItemData.ChecklistData(
                    list = ChecklistKind.entries.firstOrNull { it.name == doc["list"] } ?: ChecklistKind.BEFORE_TRIP,
                    text = doc.string("text")!!,
                    checked = doc["checked"] as Boolean,
                    dueDate = doc.string("dueDate")?.let { LocalDate.parse(it) },
                    createdAt = doc.long("createdAt")!!,
                )
                SyncKind.TRAVELER -> ItemData.TravelerData(
                    name = doc.string("name")!!,
                    color = TravelerColor.fromName(doc.string("color")),
                    createdAt = doc.long("createdAt")!!,
                )
                SyncKind.SETTLEMENT -> ItemData.SettlementData(
                    fromKey = doc.string("fromTraveler")!!,
                    toKey = doc.string("toTraveler")!!,
                    amountAgorot = doc.long("amountAgorot")!!,
                    date = LocalDate.parse(doc.string("date")),
                    createdAt = doc.long("createdAt")!!,
                )
            }
            SyncItem(kind, uuid, tripUuid, updatedAt, data)
        }.getOrNull()

    /** All four foreign fields together, or none (an expense entered in shekels). */
    private fun foreignAmount(doc: Map<String, Any?>): ForeignAmount? {
        val amount = doc.string("originalAmount") ?: return null
        val currency = doc.string("originalCurrency") ?: return null
        val rate = doc.string("exchangeRate") ?: return null
        val rateDate = doc.string("rateDate") ?: return null
        return ForeignAmount(BigDecimal(amount), currency, BigDecimal(rate), LocalDate.parse(rateDate))
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as String?

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as Number?)?.toLong()
}
