package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.TripColor
import java.time.LocalDate

/** Sample sync items for the sync tests. */
object SyncTestData {
    fun trip(uuid: String, updatedAt: Long, destination: String = "Rome") = SyncItem(
        kind = SyncKind.TRIP,
        uuid = uuid,
        tripUuid = null,
        updatedAt = updatedAt,
        data = ItemData.TripData(
            destination = destination,
            startDate = LocalDate.of(2026, 11, 12),
            endDate = LocalDate.of(2026, 11, 17),
            color = TripColor.SKY,
            budgetAgorot = 600_000,
            currencyCode = "EUR",
        ),
    )

    fun expense(uuid: String, tripUuid: String, updatedAt: Long, name: String = "Dinner", amountAgorot: Long = 12_050) =
        SyncItem(
            kind = SyncKind.EXPENSE,
            uuid = uuid,
            tripUuid = tripUuid,
            updatedAt = updatedAt,
            data = ItemData.ExpenseData(name, amountAgorot, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13), null),
        )

    fun activity(uuid: String, tripUuid: String, updatedAt: Long, title: String = "Colosseum") = SyncItem(
        kind = SyncKind.ACTIVITY,
        uuid = uuid,
        tripUuid = tripUuid,
        updatedAt = updatedAt,
        data = ItemData.ActivityData(
            ActivityInput(
                date = LocalDate.of(2026, 11, 13),
                startTime = null,
                title = title,
                type = ActivityType.ACTIVITY,
                note = null,
                location = null,
                estimatedCostAgorot = null,
                booked = false,
            )
        ),
    )

    fun checklistItem(
        uuid: String,
        tripUuid: String,
        updatedAt: Long,
        text: String = "Passport",
        checked: Boolean = false,
        dueDate: LocalDate? = LocalDate.of(2026, 11, 1),
    ) = SyncItem(
        kind = SyncKind.CHECKLIST,
        uuid = uuid,
        tripUuid = tripUuid,
        updatedAt = updatedAt,
        data = ItemData.ChecklistData(ChecklistKind.BEFORE_TRIP, text, checked, dueDate, createdAt = 42),
    )

    fun SyncItem.deletedAt(at: Long) = copy(updatedAt = at, data = null)

    fun SyncItem.renamed(destination: String, at: Long) =
        copy(updatedAt = at, data = (data as ItemData.TripData).copy(destination = destination))

    fun pending(item: SyncItem) = LocalItem(item, pending = true)

    fun synced(item: SyncItem) = LocalItem(item, pending = false)
}
