package com.example.planyourtrip

import com.example.planyourtrip.data.trip.MergeTravelerResult
import com.example.planyourtrip.data.trip.RemoveTravelerResult
import com.example.planyourtrip.data.trip.Settlement
import com.example.planyourtrip.data.trip.SettlementRepository
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerColor
import com.example.planyourtrip.data.trip.TravelerMergeInput
import com.example.planyourtrip.data.trip.TravelerMergePlan
import com.example.planyourtrip.data.trip.TravelerRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * In-memory [TravelerRepository] for unit tests. Mirrors RoomTravelerRepository: only the owner of the trip (looked
 * up in [trips]) sees and changes its travelers, the color is round-robin, and a traveler used by an expense (in
 * [expenses]) or a settlement (in [settlements]) can't be removed. [gate] holds every write until completed.
 */
class FakeTravelerRepository(
    private val trips: FakeTripRepository,
    private val expenses: FakeExpenseRepository? = null,
    initial: List<Traveler> = emptyList(),
) : TravelerRepository {
    val travelers = MutableStateFlow(initial)

    /** Set by tests that also use settlements, for the "can't be removed" check. */
    var settlements: FakeSettlementRepository? = null

    var gate: CompletableDeferred<Unit>? = null

    val added = mutableListOf<String>()

    private fun owns(ownerId: String, tripId: Long) =
        trips.trips.value.any { (owner, trip) -> owner == ownerId && trip.id == tripId }

    override fun observeTravelers(tripId: Long, ownerId: String): Flow<List<Traveler>> =
        combine(travelers, trips.trips) { list, _ ->
            if (!owns(ownerId, tripId)) emptyList() else list.filter { it.tripId == tripId }
        }

    override suspend fun addTraveler(tripId: Long, ownerId: String, name: String): Long? {
        gate?.await()
        if (name.isBlank() || !owns(ownerId, tripId)) return null
        val id = (travelers.value.maxOfOrNull { it.id } ?: 0) + 1
        val count = travelers.value.count { it.tripId == tripId }
        added += name.trim()
        travelers.update { it + Traveler(id, tripId, "t$id", name.trim(), TravelerColor.forIndex(count)) }
        return id
    }

    override suspend fun renameTraveler(id: Long, ownerId: String, name: String) {
        gate?.await()
        travelers.update { list ->
            list.map { if (it.id == id && owns(ownerId, it.tripId)) it.copy(name = name.trim()) else it }
        }
    }

    override suspend fun removeTraveler(id: Long, ownerId: String): RemoveTravelerResult {
        gate?.await()
        val traveler = travelers.value.firstOrNull { it.id == id && owns(ownerId, it.tripId) }
            ?: return RemoveTravelerResult.NOT_FOUND
        val key = traveler.uuid
        val usedByExpense = expenses?.expenses?.value.orEmpty()
            .any { it.tripId == traveler.tripId && (it.paidBy == key || key in it.splitBetween) }
        val usedBySettlement = settlements?.settlements?.value.orEmpty()
            .any { it.tripId == traveler.tripId && (it.fromKey == key || it.toKey == key) }
        if (usedByExpense || usedBySettlement) return RemoveTravelerResult.IN_USE
        travelers.update { list -> list.filterNot { it.id == id } }
        return RemoveTravelerResult.REMOVED
    }

    /** [merges] = (traveler id, member uid) of every merge that was written. */
    val merges = mutableListOf<Pair<Long, String>>()

    /** Like Room: plans against the current expenses and settlements, then writes all of it or nothing. */
    override suspend fun mergeIntoMember(
        tripId: Long,
        ownerId: String,
        id: Long,
        memberUid: String,
        plan: (TravelerMergeInput) -> TravelerMergePlan,
    ): MergeTravelerResult {
        gate?.await()
        val trip = trips.trips.value.firstOrNull { (owner, t) -> owner == ownerId && t.id == tripId }?.second
            ?: return MergeTravelerResult.NOT_FOUND
        val traveler = travelers.value.firstOrNull { it.id == id && it.tripId == tripId }
            ?: return MergeTravelerResult.NOT_FOUND
        if (memberUid !in trip.memberIds) return MergeTravelerResult.NOT_FOUND
        val tripExpenses = expenses?.expenses?.value.orEmpty().filter { it.tripId == tripId }
        val tripSettlements = settlements?.settlements?.value.orEmpty().filter { it.tripId == tripId }
        val input = TravelerMergeInput(
            traveler.uuid, memberUid, ownerId, trip.memberIds, travelers.value.filter { it.tripId == tripId },
            tripExpenses, tripSettlements,
        )
        val changes = (plan(input) as? TravelerMergePlan.Ready)?.changes ?: return MergeTravelerResult.BLOCKED
        expenses?.expenses?.update { list -> list.map { e -> changes.expenses.firstOrNull { it.id == e.id } ?: e } }
        settlements?.settlements?.update { list ->
            list.filterNot { it.id in changes.deletedSettlementIds }
                .map { s -> changes.settlements.firstOrNull { it.id == s.id } ?: s }
        }
        travelers.update { list -> list.filterNot { it.id == id } }
        merges += id to memberUid
        return MergeTravelerResult.MERGED
    }
}

/** In-memory [SettlementRepository]; newest first like Room. [gate] holds every write until completed. */
class FakeSettlementRepository(
    private val trips: FakeTripRepository,
    initial: List<Settlement> = emptyList(),
) : SettlementRepository {
    val settlements = MutableStateFlow(initial)

    var gate: CompletableDeferred<Unit>? = null

    private fun owns(ownerId: String, tripId: Long) =
        trips.trips.value.any { (owner, trip) -> owner == ownerId && trip.id == tripId }

    override fun observeSettlements(tripId: Long, ownerId: String): Flow<List<Settlement>> =
        combine(settlements, trips.trips) { list, _ ->
            if (!owns(ownerId, tripId)) emptyList()
            else list.filter { it.tripId == tripId }
                .sortedWith(compareByDescending<Settlement> { it.date }.thenByDescending { it.id })
        }

    override suspend fun addSettlement(
        tripId: Long,
        ownerId: String,
        fromKey: String,
        toKey: String,
        amountAgorot: Long,
        date: LocalDate,
    ): Long? {
        gate?.await()
        if (amountAgorot <= 0 || fromKey == toKey || !owns(ownerId, tripId)) return null
        val id = (settlements.value.maxOfOrNull { it.id } ?: 0) + 1
        settlements.update { it + Settlement(id, tripId, "s$id", fromKey, toKey, amountAgorot, date) }
        return id
    }

    override suspend fun deleteSettlement(id: Long, ownerId: String) {
        gate?.await()
        settlements.update { list -> list.filterNot { it.id == id && owns(ownerId, it.tripId) } }
    }
}
