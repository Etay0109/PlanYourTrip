package com.example.planyourtrip.data.reminder

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.Clock
import java.time.LocalDate

/** Told when the user created or joined a trip: the natural moment to ask for the notification permission. */
fun interface ReminderPrompt {
    fun onTripAdded(startDate: LocalDate)

    companion object {
        /** Asks nothing (tests that don't care). */
        val None = ReminderPrompt { }
    }
}

/**
 * Asks for the notification permission once, after the first trip added that starts in the future. The request is
 * shown by `NotificationPermissionHost` (it needs an Activity), which calls [onAsked].
 */
class ReminderPermissionPrompt(
    private val settings: ReminderSettings,
    private val clock: Clock,
) : ReminderPrompt {
    private val _requests = Channel<Unit>(Channel.CONFLATED)

    /** One element per time the permission should be asked for now. */
    val requests: Flow<Unit> = _requests.receiveAsFlow()

    override fun onTripAdded(startDate: LocalDate) {
        if (settings.permissionAsked) return
        if (!startDate.isAfter(LocalDate.now(clock))) return
        _requests.trySend(Unit)
    }

    /** The system dialog was shown: never ask this way again, whatever the answer. */
    fun onAsked() {
        settings.permissionAsked = true
    }
}
