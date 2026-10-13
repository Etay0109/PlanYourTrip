package com.example.planyourtrip.data.reminder

import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TripRemindersTest {
    private val jerusalem = ZoneId.of("Asia/Jerusalem")
    private val london = ZoneId.of("Europe/London")

    private fun trip(uuid: String, start: LocalDate, days: Long = 3) =
        Trip(id = uuid.hashCode().toLong(), destination = "Rome", startDate = start, endDate = start.plusDays(days),
            color = TripColor.SKY, uuid = uuid)

    private fun at(text: String, zone: ZoneId): Instant = ZonedDateTime.of(java.time.LocalDateTime.parse(text), zone).toInstant()

    // reminderTime

    @Test
    fun reminderIs1800TheDayBefore_inTheDeviceZone() {
        assertEquals(Instant.parse("2026-10-13T15:00:00Z"), reminderTime(LocalDate.of(2026, 10, 14), jerusalem))
        assertEquals(at("2026-11-11T18:00", london), reminderTime(LocalDate.of(2026, 11, 12), london))
    }

    @Test
    fun daylightSavingEnds_theReminderStaysAt1800LocalTime() {
        // London leaves summer time on 25 Oct 2026 at 02:00.
        assertEquals(Instant.parse("2026-10-24T17:00:00Z"), reminderTime(LocalDate.of(2026, 10, 25), london))
        assertEquals(Instant.parse("2026-10-25T18:00:00Z"), reminderTime(LocalDate.of(2026, 10, 26), london))
        // 25 hours apart, not 24: both are 18:00 on the wall clock.
        val gap = java.time.Duration.between(
            reminderTime(LocalDate.of(2026, 10, 25), london), reminderTime(LocalDate.of(2026, 10, 26), london),
        )
        assertEquals(25, gap.toHours())
    }

    @Test
    fun daylightSavingStarts_theReminderStaysAt1800LocalTime() {
        // London starts summer time on 29 Mar 2026 at 01:00.
        assertEquals(Instant.parse("2026-03-28T18:00:00Z"), reminderTime(LocalDate.of(2026, 3, 29), london))
        assertEquals(Instant.parse("2026-03-29T17:00:00Z"), reminderTime(LocalDate.of(2026, 3, 30), london))
    }

    @Test
    fun plannedBeforeTheClockChange_firesAt1800AfterIt() {
        val now = at("2026-10-20T09:00", london) // summer time
        val plan = planReminders("u1", listOf(trip("a", LocalDate.of(2026, 10, 27))), now, london, enabled = true)
        assertEquals(at("2026-10-26T18:00", london), plan.getValue("a").at)
        assertEquals(Instant.parse("2026-10-26T18:00:00Z"), plan.getValue("a").at)
    }

    // planReminders

    @Test
    fun futureTrips_getOneReminderEach_forTheSignedInAccount() {
        val now = at("2026-10-12T10:00", jerusalem)
        val trips = listOf(trip("a", LocalDate.of(2026, 10, 14)), trip("b", LocalDate.of(2026, 12, 1)))
        val plan = planReminders("u1", trips, now, jerusalem, enabled = true)
        assertEquals(
            mapOf(
                "a" to Reminder("u1", at("2026-10-13T18:00", jerusalem)),
                "b" to Reminder("u1", at("2026-11-30T18:00", jerusalem)),
            ),
            plan,
        )
    }

    @Test
    fun tripsStartingTodayOrStartedOrPast_getNone() {
        val now = at("2026-10-12T08:00", jerusalem)
        val trips = listOf(
            trip("today", LocalDate.of(2026, 10, 12)),
            trip("started", LocalDate.of(2026, 10, 10)),
            trip("past", LocalDate.of(2026, 9, 1)),
        )
        assertEquals(emptyMap<String, Reminder>(), planReminders("u1", trips, now, jerusalem, enabled = true))
    }

    @Test
    fun startingTomorrow_beforeSix_getsOne_firstSeenAfterSix_getsNone() {
        val tomorrow = listOf(trip("a", LocalDate.of(2026, 10, 13)))
        assertEquals(
            setOf("a"),
            planReminders("u1", tomorrow, at("2026-10-12T17:59", jerusalem), jerusalem, enabled = true).keys,
        )
        // Created (or synced to this phone) the evening before, after 18:00: no reminder.
        assertTrue(planReminders("u1", tomorrow, at("2026-10-12T18:30", jerusalem), jerusalem, true).isEmpty())
        assertTrue(planReminders("u1", tomorrow, at("2026-10-12T18:00", jerusalem), jerusalem, true).isEmpty())
    }

    @Test
    fun aDelayedReminder_staysOwedUntilMidnight_neverAfter() {
        val start = LocalDate.of(2026, 10, 14)
        val trips = listOf(trip("a", start))
        val scheduled = mapOf("a" to Reminder("u1", reminderTime(start, jerusalem)))
        fun owed(time: String) =
            planReminders("u1", trips, at(time, jerusalem), jerusalem, true, scheduled).keys == setOf("a")
        assertTrue(owed("2026-10-13T18:00"))
        assertTrue(owed("2026-10-13T18:01"))
        assertTrue(owed("2026-10-13T23:30"))
        assertTrue(owed("2026-10-13T23:59:59"))
        assertFalse(owed("2026-10-14T00:00"))
        assertFalse(owed("2026-10-14T07:00"))
        // Scheduled for another account or another time: not owed after 18:00.
        val other = mapOf("a" to Reminder("u2", reminderTime(start, jerusalem)))
        assertTrue(planReminders("u1", trips, at("2026-10-13T19:00", jerusalem), jerusalem, true, other).isEmpty())
    }

    @Test
    fun aShownReminder_isNotOwed_butMovedDatesGetANewOne() {
        val shown = setOf(shownKey("a", "u1", LocalDate.of(2026, 10, 14)))
        val now = at("2026-10-12T10:00", jerusalem)
        fun plan(start: LocalDate) = planReminders("u1", listOf(trip("a", start)), now, jerusalem, true, shown = shown)
        assertTrue(plan(LocalDate.of(2026, 10, 14)).isEmpty())
        assertEquals(setOf("a"), plan(LocalDate.of(2026, 10, 20)).keys)
    }

    @Test
    fun deadlineIsMidnightOnTheStartDay_andShownKeysKeepTheirDate() {
        assertEquals(at("2026-10-14T00:00", jerusalem), reminderDeadline(LocalDate.of(2026, 10, 14), jerusalem))
        assertEquals(LocalDate.of(2026, 10, 14), shownKeyStart(shownKey("a", "u1", LocalDate.of(2026, 10, 14))))
        assertEquals(null, shownKeyStart("garbage"))
        assertTrue(Reminder("u1", at("2026-10-13T18:00", jerusalem)).isDue(at("2026-10-13T18:00", jerusalem)))
        assertFalse(Reminder("u1", at("2026-10-13T18:00", jerusalem)).isDue(at("2026-10-13T17:59", jerusalem)))
    }

    @Test
    fun switchOff_orNobodySignedIn_wantsNothing() {
        val now = at("2026-10-12T10:00", jerusalem)
        val trips = listOf(trip("a", LocalDate.of(2026, 11, 1)))
        assertTrue(planReminders("u1", trips, now, jerusalem, enabled = false).isEmpty())
        assertTrue(planReminders(null, trips, now, jerusalem, enabled = true).isEmpty())
    }

    // reminderChanges

    @Test
    fun changes_newMovedRemovedAndUnchanged() {
        val r1 = Reminder("u1", Instant.parse("2026-11-01T16:00:00Z"))
        val r2 = Reminder("u1", Instant.parse("2026-12-01T16:00:00Z"))
        val moved = Reminder("u1", Instant.parse("2026-11-05T16:00:00Z"))
        val changes = reminderChanges(
            desired = mapOf("same" to r1, "edited" to moved, "new" to r2),
            scheduled = mapOf("same" to r1, "edited" to r1, "deleted" to r2),
        )
        assertEquals(mapOf("edited" to moved, "new" to r2), changes.toSchedule)
        assertEquals(setOf("deleted"), changes.toCancel)
    }

    @Test
    fun changes_anotherAccountsReminderForTheSameTrip_isReplaced() {
        val time = Instant.parse("2026-11-01T16:00:00Z")
        val changes = reminderChanges(mapOf("a" to Reminder("u2", time)), mapOf("a" to Reminder("u1", time)))
        assertEquals(mapOf("a" to Reminder("u2", time)), changes.toSchedule)
        assertTrue(changes.toCancel.isEmpty())
    }

    @Test
    fun editedDates_moveTheReminder_deletedOrLeftTrips_areCancelled() {
        val now = at("2026-10-12T10:00", jerusalem)
        val before = planReminders(
            "u1", listOf(trip("a", LocalDate.of(2026, 11, 1)), trip("b", LocalDate.of(2026, 11, 8))), now, jerusalem, true,
        )
        // "a" moved a week later; "b" deleted (or left / removed: gone from Room).
        val after = planReminders("u1", listOf(trip("a", LocalDate.of(2026, 11, 8))), now, jerusalem, true)
        val changes = reminderChanges(after, before)
        assertEquals(at("2026-11-07T18:00", jerusalem), changes.toSchedule.getValue("a").at)
        assertEquals(setOf("b"), changes.toCancel)
    }

    // reminderMessage

    @Test
    fun message_dependsOnTheBeforeTheTripList() {
        assertEquals(ReminderMessage.NoList, reminderMessage(BeforeTripCount(open = 0, total = 0)))
        assertEquals(ReminderMessage.AllDone, reminderMessage(BeforeTripCount(open = 0, total = 5)))
        assertEquals(ReminderMessage.ItemsLeft(1), reminderMessage(BeforeTripCount(open = 1, total = 5)))
        assertEquals(ReminderMessage.ItemsLeft(3), reminderMessage(BeforeTripCount(open = 3, total = 3)))
    }

    @Test
    fun title_hasTheFlagAndTheShortName_onlyForAPickedPlace() {
        assertEquals("🇬🇧 London", reminderDestination("London, United Kingdom", "GB"))
        assertEquals("Rome, Italy", reminderDestination("Rome, Italy", null))
    }

    // shouldShowReminder

    private fun show(
        uid: String? = "u1",
        start: LocalDate? = LocalDate.of(2026, 10, 13),
        today: LocalDate = LocalDate.of(2026, 10, 12),
        enabled: Boolean = true,
        allowed: Boolean = true,
    ) = shouldShowReminder("u1", uid, start, today, enabled, allowed)

    @Test
    fun shownOnlyWhenStillWanted() {
        assertTrue(show())
        assertFalse(show(uid = null)) // signed out
        assertFalse(show(uid = "u2")) // another account
        assertFalse(show(start = null)) // deleted or left
        assertFalse(show(start = LocalDate.of(2026, 10, 20))) // dates edited
        assertFalse(show(today = LocalDate.of(2026, 10, 13))) // ran only after midnight
        assertFalse(show(enabled = false)) // switch off
        assertFalse(show(allowed = false)) // permission refused
    }
}
