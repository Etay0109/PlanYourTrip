package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.ForeignAmount
import com.example.planyourtrip.data.trip.LEGACY_ME_KEY
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
 * [DELETED] = true and [UPDATED_AT] (a trip tombstone also keeps [OWNER_ID] / [MEMBER_IDS], see FirestoreCloudStore).
 */
object SyncDocuments {
    const val UPDATED_AT = "updatedAt"
    const val DELETED = "deleted"

    /** The trip's owner and members: who may read and write it (firestore.rules). */
    const val OWNER_ID = "ownerId"
    const val MEMBER_IDS = "memberIds"

    /** The trip document's data fields: what an upload of an existing trip changes (never owner or members). */
    val TRIP_DATA_FIELDS = listOf(
        Field.DESTINATION, Field.START_DATE, Field.END_DATE, Field.CURRENCY_CODE, Field.COLOR,
    )

    /** Document field names (besides [DELETED] and [UPDATED_AT]); written and read with the same constants. */
    private object Field {
        const val DESTINATION = "destination"
        const val START_DATE = "startDate"
        const val END_DATE = "endDate"
        const val BUDGET_AGOROT = "budgetAgorot"
        const val CURRENCY_CODE = "currencyCode"
        const val COLOR = "color"
        const val NAME = "name"
        const val AMOUNT_AGOROT = "amountAgorot"
        const val CATEGORY = "category"
        const val DATE = "date"
        const val ORIGINAL_AMOUNT = "originalAmount"
        const val ORIGINAL_CURRENCY = "originalCurrency"
        const val EXCHANGE_RATE = "exchangeRate"
        const val RATE_DATE = "rateDate"
        const val PAID_BY = "paidBy"
        const val SPLIT_BETWEEN = "splitBetween"
        const val START_TIME = "startTime"
        const val TITLE = "title"
        const val NOTE = "note"
        const val TYPE = "type"
        const val LOCATION = "location"
        const val ESTIMATED_COST_AGOROT = "estimatedCostAgorot"
        const val BOOKED = "booked"
        const val LIST = "list"
        const val TEXT = "text"
        const val CHECKED = "checked"
        const val DUE_DATE = "dueDate"
        const val CREATED_AT = "createdAt"
        const val FROM_TRAVELER = "fromTraveler"
        const val TO_TRAVELER = "toTraveler"
    }

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun toDocument(item: SyncItem): Map<String, Any?> {
        val fields: Map<String, Any?> = when (val data = item.data) {
            null -> return mapOf(DELETED to true, UPDATED_AT to item.updatedAt)
            is ItemData.TripData -> mapOf(
                Field.DESTINATION to data.destination,
                Field.START_DATE to data.startDate.toString(),
                Field.END_DATE to data.endDate.toString(),
                Field.CURRENCY_CODE to data.currencyCode,
                Field.COLOR to data.color.name,
                OWNER_ID to data.ownerId,
                MEMBER_IDS to data.memberIds,
            )
            is ItemData.BudgetData -> mapOf(Field.BUDGET_AGOROT to data.budgetAgorot)
            is ItemData.ExpenseData -> mapOf(
                Field.NAME to data.name,
                Field.AMOUNT_AGOROT to data.amountAgorot,
                Field.CATEGORY to data.category.name,
                Field.DATE to data.date.toString(),
                Field.ORIGINAL_AMOUNT to data.original?.amount?.toPlainString(),
                Field.ORIGINAL_CURRENCY to data.original?.currencyCode,
                Field.EXCHANGE_RATE to data.original?.ilsPerUnit?.toPlainString(),
                Field.RATE_DATE to data.original?.rateDate?.toString(),
                Field.PAID_BY to data.paidBy,
                Field.SPLIT_BETWEEN to data.splitBetween,
            )
            is ItemData.ActivityData -> with(data.input) {
                mapOf(
                    Field.DATE to date.toString(),
                    Field.START_TIME to startTime?.format(TIME),
                    Field.TITLE to title,
                    Field.NOTE to note,
                    Field.TYPE to type.name,
                    Field.LOCATION to location,
                    Field.ESTIMATED_COST_AGOROT to estimatedCostAgorot,
                    Field.BOOKED to booked,
                )
            }
            is ItemData.ChecklistData -> mapOf(
                Field.LIST to data.list.name,
                Field.TEXT to data.text,
                Field.CHECKED to data.checked,
                Field.DUE_DATE to data.dueDate?.toString(),
                Field.CREATED_AT to data.createdAt,
            )
            is ItemData.TravelerData -> mapOf(
                Field.NAME to data.name,
                Field.COLOR to data.color.name,
                Field.CREATED_AT to data.createdAt,
            )
            is ItemData.SettlementData -> mapOf(
                Field.FROM_TRAVELER to data.fromKey,
                Field.TO_TRAVELER to data.toKey,
                Field.AMOUNT_AGOROT to data.amountAgorot,
                Field.DATE to data.date.toString(),
                Field.CREATED_AT to data.createdAt,
            )
        }
        return fields + mapOf(DELETED to false, UPDATED_AT to item.updatedAt)
    }

    /**
     * Reads a cloud document; null when it can't be read (missing or wrong fields), so one bad document never
     * stops a sync. Unknown enum names fall back like in Room (first color, OTHER, BEFORE_TRIP). An expense without
     * `paidBy` / `splitBetween` (only in the legacy copy, written before expense splitting) is paid by
     * [LEGACY_ME_KEY], not shared. A legacy trip document has no owner / members ("" / empty until the migration
     * fills them in).
     */
    fun fromDocument(kind: SyncKind, uuid: String, tripUuid: String?, doc: Map<String, Any?>): SyncItem? =
        runCatching {
            val updatedAt = (doc[UPDATED_AT] as Number).toLong()
            val data = if (doc[DELETED] == true) null else when (kind) {
                SyncKind.TRIP -> ItemData.TripData(
                    destination = doc.string(Field.DESTINATION)!!,
                    startDate = LocalDate.parse(doc.string(Field.START_DATE)),
                    endDate = LocalDate.parse(doc.string(Field.END_DATE)),
                    color = TripColor.fromName(doc[Field.COLOR] as? String),
                    currencyCode = doc.string(Field.CURRENCY_CODE),
                    ownerId = doc.string(OWNER_ID).orEmpty(),
                    memberIds = (doc[MEMBER_IDS] as List<*>?).orEmpty().filterIsInstance<String>(),
                )
                SyncKind.BUDGET -> ItemData.BudgetData(doc.long(Field.BUDGET_AGOROT))
                SyncKind.EXPENSE -> {
                    // Legacy documents written before expense splitting have no payer or split: paid by me, not shared.
                    val paidBy = doc.string(Field.PAID_BY)?.takeIf { it.isNotEmpty() } ?: LEGACY_ME_KEY
                    val split = (doc[Field.SPLIT_BETWEEN] as List<*>?).orEmpty()
                        .filterIsInstance<String>().filter { it.isNotEmpty() }.distinct()
                    ItemData.ExpenseData(
                        name = doc.string(Field.NAME)!!,
                        amountAgorot = doc.long(Field.AMOUNT_AGOROT)!!,
                        category = ExpenseCategory.fromName(doc[Field.CATEGORY] as? String),
                        date = LocalDate.parse(doc.string(Field.DATE)),
                        original = foreignAmount(doc),
                        paidBy = paidBy,
                        splitBetween = split.ifEmpty { listOf(paidBy) },
                    )
                }
                SyncKind.ACTIVITY -> ItemData.ActivityData(
                    ActivityInput(
                        date = LocalDate.parse(doc.string(Field.DATE)),
                        startTime = doc.string(Field.START_TIME)?.let { LocalTime.parse(it, TIME) },
                        title = doc.string(Field.TITLE)!!,
                        type = ActivityType.fromName(doc[Field.TYPE] as? String),
                        note = doc.string(Field.NOTE),
                        location = doc.string(Field.LOCATION),
                        estimatedCostAgorot = doc.long(Field.ESTIMATED_COST_AGOROT),
                        booked = doc[Field.BOOKED] as Boolean,
                    ),
                    // Older documents may still hold "placeId" / "placeLookup" (removed with the Map tab): ignored.
                )
                SyncKind.CHECKLIST, SyncKind.PACKING -> ItemData.ChecklistData(
                    list = ChecklistKind.fromName(doc[Field.LIST] as? String),
                    text = doc.string(Field.TEXT)!!,
                    checked = doc[Field.CHECKED] as Boolean,
                    dueDate = doc.string(Field.DUE_DATE)?.let { LocalDate.parse(it) },
                    createdAt = doc.long(Field.CREATED_AT)!!,
                )
                SyncKind.TRAVELER -> ItemData.TravelerData(
                    name = doc.string(Field.NAME)!!,
                    color = TravelerColor.fromName(doc.string(Field.COLOR)),
                    createdAt = doc.long(Field.CREATED_AT)!!,
                )
                SyncKind.SETTLEMENT -> ItemData.SettlementData(
                    fromKey = doc.string(Field.FROM_TRAVELER)!!,
                    toKey = doc.string(Field.TO_TRAVELER)!!,
                    amountAgorot = doc.long(Field.AMOUNT_AGOROT)!!,
                    date = LocalDate.parse(doc.string(Field.DATE)),
                    createdAt = doc.long(Field.CREATED_AT)!!,
                )
            }
            SyncItem(kind, uuid, tripUuid, updatedAt, data)
        }.getOrNull()

    /**
     * The budget kept in a **legacy** trip document (before shared trips): the owner's [SyncKind.BUDGET] item with
     * the trip's `updatedAt`, or null for a tombstone or an unreadable document.
     */
    fun legacyBudget(tripUuid: String, doc: Map<String, Any?>): SyncItem? = runCatching {
        if (doc[DELETED] == true) return null
        SyncItem(
            SyncKind.BUDGET, tripUuid, tripUuid, (doc[UPDATED_AT] as Number).toLong(),
            ItemData.BudgetData(doc.long(Field.BUDGET_AGOROT)),
        )
    }.getOrNull()

    /** All four foreign fields together, or none (an expense entered in shekels). */
    private fun foreignAmount(doc: Map<String, Any?>): ForeignAmount? {
        val amount = doc.string(Field.ORIGINAL_AMOUNT) ?: return null
        val currency = doc.string(Field.ORIGINAL_CURRENCY) ?: return null
        val rate = doc.string(Field.EXCHANGE_RATE) ?: return null
        val rateDate = doc.string(Field.RATE_DATE) ?: return null
        return ForeignAmount(BigDecimal(amount), currency, BigDecimal(rate), LocalDate.parse(rateDate))
    }

    private fun Map<String, Any?>.string(key: String): String? = this[key] as String?

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as Number?)?.toLong()
}
