package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * Keeps one "trip starts tomorrow" reminder per future trip of the signed-in account, from Room: a trip created,
 * edited or synced is (re)scheduled; deleted, left or removed is cancelled; signing out cancels everything; the
 * switch turns them all off and on. The first pass after each app start checks the real [ReminderWork] state, so a
 * reminder the saved list remembers but the scheduler lost is scheduled again.
 */
class TripReminderScheduler(
    private val authRepository: AuthRepository,
    private val tripRepository: TripRepository,
    private val settings: ReminderSettings,
    private val work: ReminderWork,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    private var checkedPending = false

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        scope.launch {
            val accountTrips = authRepository.currentUser.flatMapLatest { user ->
                if (user == null) flowOf(null to emptyList())
                else tripRepository.observeTrips(user.uid).map { user.uid to it }
            }
            combine(accountTrips, settings.enabled) { (uid, trips), enabled -> Triple(uid, trips, enabled) }
                .collect { (uid, trips, enabled) -> reconcile(uid, trips, enabled) }
        }
    }

    private suspend fun reconcile(uid: String?, trips: List<Trip>, enabled: Boolean) {
        var scheduled = settings.scheduled()
        if (!checkedPending) {
            // Trust only what is really pending: anything else is scheduled again, unknown leftovers cancelled.
            val pending = work.pendingUuids()
            (pending - scheduled.keys).forEach(work::cancel)
            scheduled = scheduled.filterKeys { it in pending }
            checkedPending = true
        }
        if (uid == null) {
            if (scheduled.isNotEmpty()) work.cancelAll()
            settings.setScheduled(emptyMap())
            return
        }
        val desired = planReminders(uid, trips, clock.instant(), clock.zone, enabled)
        val changes = reminderChanges(desired, scheduled)
        changes.toCancel.forEach(work::cancel)
        changes.toSchedule.forEach { (uuid, reminder) -> work.schedule(uuid, reminder.uid, reminder.at) }
        settings.setScheduled(desired)
    }
}
