package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * Keeps one "trip starts tomorrow" reminder per future trip of the signed-in account, from Room: a trip created,
 * edited or synced is (re)scheduled; deleted, left or removed is cancelled; signing out cancels everything; the
 * switch turns them all off and on.
 *
 * A reminder stays owed after its 18:00 until it is shown or midnight comes ([planReminders]), so a delayed one is
 * never cancelled (until 2026-10-13 a reminder that ran even a moment late, or the app start it caused, cancelled
 * it). An owed reminder whose time has come is shown right away through [delivery] (at most once): this is the check
 * at every app start, after a restart or an update ([ReminderRestoreReceiver]) and after a force-stop. The first pass
 * after each app start schedules every future reminder again, because alarms don't survive a restart or a force-stop.
 */
class TripReminderScheduler(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val settings: ReminderSettings,
    private val work: ReminderWork,
    private val delivery: ReminderDelivery,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private var firstPassDone = false
    private val firstPass = CompletableDeferred<Unit>()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            val accountTrips = authRepository.currentUser.flatMapLatest { user ->
                if (user == null) flowOf(null to emptyList())
                else tripRepository.observeTrips(user.uid).map { user.uid to it }
            }
            combine(accountTrips, settings.enabled) { (uid, trips), enabled -> Triple(uid, trips, enabled) }
                .collect { (uid, trips, enabled) ->
                    reconcile(uid, trips, enabled)
                    firstPass.complete(Unit)
                }
        }
    }

    /** Waits until the first pass after the app start is done (alarms armed again, a missed reminder shown). */
    suspend fun awaitFirstPass() = firstPass.await()

    private suspend fun reconcile(uid: String?, trips: List<Trip>, enabled: Boolean) {
        val now = clock.instant()
        settings.pruneShown(LocalDate.now(clock))
        val scheduled = settings.scheduled()
        val isFirstPass = !firstPassDone
        if (isFirstPass) {
            // WorkManager requests nothing remembers (left over from an older install) are cancelled.
            (work.pendingUuids() - scheduled.keys).forEach(work::cancel)
            firstPassDone = true
        }
        if (uid == null) {
            if (scheduled.isNotEmpty()) work.cancelAll(scheduled.keys)
            settings.setScheduled(emptyMap())
            return
        }
        val desired = planReminders(uid, trips, now, clock.zone, enabled, scheduled, settings.shown())
        val changes = reminderChanges(desired, scheduled)
        changes.toCancel.forEach(work::cancel)
        (if (isFirstPass) desired else changes.toSchedule)
            .filterValues { !it.isDue(now) }
            .forEach { (uuid, reminder) -> work.schedule(uuid, reminder.uid, reminder.at) }
        settings.setScheduled(desired)
        // Owed and due: the alarm was missed or is late (force-stop, restart, the app starting for it right now).
        desired.filterValues { it.isDue(now) }.forEach { (uuid, reminder) -> delivery.deliver(uuid, reminder.uid) }
    }
}
