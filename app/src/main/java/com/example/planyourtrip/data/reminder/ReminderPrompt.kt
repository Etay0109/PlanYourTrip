package com.example.planyourtrip.data.reminder

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Clock
import java.time.LocalDate

/**
 * Told when the user created or joined a trip, or turned the reminders on: the natural moments to ask for the
 * notification permission and to offer the battery screen.
 */
fun interface ReminderPrompt {
    fun onTripAdded(startDate: LocalDate)

    /** The profile menu's "Trip reminders" switch was turned on (the menu asks for the permission itself). */
    fun onRemindersTurnedOn() {}

    companion object {
        /** Asks nothing (tests that don't care). */
        val None = ReminderPrompt { }
    }
}

/** One moment to set reminders up: the notification permission first ([askPermission]), then the battery screen. */
data class ReminderSetupRequest(val askPermission: Boolean)

/**
 * Asks for the notification permission once, after the first trip added that starts in the future, and offers the
 * one-time battery screen after it (or when the reminders are turned on). Both are shown by
 * `NotificationPermissionHost` (it needs an Activity), which calls [onAsked] and [onBatteryTipShown].
 */
class ReminderPermissionPrompt(
    private val settings: ReminderSettings,
    private val clock: Clock,
) : ReminderPrompt {
    private val _requests = Channel<ReminderSetupRequest>(Channel.CONFLATED)

    /** One element per time the permission or the battery screen should be shown now. */
    val requests: Flow<ReminderSetupRequest> = _requests.receiveAsFlow()

    override fun onTripAdded(startDate: LocalDate) {
        if (!startDate.isAfter(LocalDate.now(clock))) return
        when {
            !settings.permissionAsked -> _requests.trySend(ReminderSetupRequest(askPermission = true))
            !settings.batteryTipShown -> _requests.trySend(ReminderSetupRequest(askPermission = false))
        }
    }

    override fun onRemindersTurnedOn() {
        if (!settings.batteryTipShown) _requests.trySend(ReminderSetupRequest(askPermission = false))
    }

    /** The system dialog was shown: never ask this way again, whatever the answer. */
    fun onAsked() {
        settings.permissionAsked = true
    }

    /** Whether this phone should see the battery screen now ([shouldOfferBatteryTip]). */
    fun wantsBatteryTip(manufacturer: String, exempt: Boolean): Boolean =
        shouldOfferBatteryTip(manufacturer, exempt, settings.batteryTipShown)

    /** The battery screen was shown: never again, whatever the answer. */
    fun onBatteryTipShown() {
        settings.batteryTipShown = true
    }
}
