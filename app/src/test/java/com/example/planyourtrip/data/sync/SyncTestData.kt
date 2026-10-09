package com.example.planyourtrip.data.sync

import com.example.planyourtrip.data.trip.ActivityInput
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.ChecklistKind
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.TripColor
import java.time.LocalDate

/** Sample sync items for the sync tests. Traveler keys of the account "alice" are her uid. */
object SyncTestData {
    /** A trip of [owner] (its only member), as the cloud stores it. */
    fun trip(uuid: String, updatedAt: Long, destination: String = "Rome", owner: String = "alice") = SyncItem(
        kind = SyncKind.TRIP,
        uuid = uuid,
        tripUuid = null,
        updatedAt = updatedAt,
        data = ItemData.TripData(
            destination = destination,
            startDate = LocalDate.of(2026, 11, 12),
            endDate = LocalDate.of(2026, 11, 17),
            color = TripColor.SKY,
            currencyCode = "EUR",
            ownerId = owner,
            memberIds = listOf(owner),
        ),
    )

    fun expense(
        uuid: String,
        tripUuid: String,
        updatedAt: Long,
        name: String = "Dinner",
        amountAgorot: Long = 12_050,
        paidBy: String = "alice",
    ) =
        SyncItem(
            kind = SyncKind.EXPENSE,
            uuid = uuid,
            tripUuid = tripUuid,
            updatedAt = updatedAt,
            data = ItemData.ExpenseData(
                name, amountAgorot, ExpenseCategory.FOOD, LocalDate.of(2026, 11, 13), null, paidBy, listOf(paidBy),
            ),
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
        createdAt: Long = 42,
    ) = SyncItem(
        kind = SyncKind.CHECKLIST,
        uuid = uuid,
        tripUuid = tripUuid,
        updatedAt = updatedAt,
        data = ItemData.ChecklistData(ChecklistKind.BEFORE_TRIP, text, checked, dueDate, createdAt),
    )

    fun traveler(uuid: String, tripUuid: String, updatedAt: Long, name: String = "Noa", createdAt: Long = 7) =
        SyncItem(
            kind = SyncKind.TRAVELER,
            uuid = uuid,
            tripUuid = tripUuid,
            updatedAt = updatedAt,
            data = ItemData.TravelerData(name, TravelerColor.ROSE, createdAt),
        )

    fun settlement(uuid: String, tripUuid: String, updatedAt: Long, from: String = "noa", to: String = "alice") =
        SyncItem(
            kind = SyncKind.SETTLEMENT,
            uuid = uuid,
            tripUuid = tripUuid,
            updatedAt = updatedAt,
            data = ItemData.SettlementData(from, to, 32_000, LocalDate.of(2026, 11, 14), createdAt = 11),
        )

    /** The signed-in member's own budget for trip [tripUuid]. */
    fun budget(tripUuid: String, updatedAt: Long, budgetAgorot: Long? = 600_000) =
        SyncItem(SyncKind.BUDGET, tripUuid, tripUuid, updatedAt, ItemData.BudgetData(budgetAgorot))

    fun SyncItem.deletedAt(at: Long) = copy(updatedAt = at, data = null)

    fun SyncItem.renamed(destination: String, at: Long) =
        copy(updatedAt = at, data = (data as ItemData.TripData).copy(destination = destination))

    fun pending(item: SyncItem) = LocalItem(item, pending = true)

    fun synced(item: SyncItem) = LocalItem(item, pending = false)
}
