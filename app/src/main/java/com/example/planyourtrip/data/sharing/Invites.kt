package com.example.planyourtrip.data.sharing

import java.time.LocalDate

/** The owner's open invite of a trip: [code] works once until [expiresAt] (epoch millis, server time + 7 days). */
data class Invite(val code: String, val createdAt: Long) {
    val expiresAt: Long get() = InviteCode.expiresAt(createdAt)

    /** Still open at [nowMillis] as far as this device can tell (the rules decide in the end). */
    fun isOpenAt(nowMillis: Long): Boolean = nowMillis < expiresAt
}

/** What an open invite shows before joining: only the destination, the dates and who invited me. */
data class InvitePreview(
    val code: String,
    val tripUuid: String,
    val destination: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val inviterName: String,
)

/** The result of [InviteRepository.join]. */
enum class JoinResult {
    JOINED,

    /** Wrong, expired, used or cancelled: one result for all, so a code reveals nothing. */
    INVALID_CODE,

    ALREADY_MEMBER,

    /** The trip already has the maximum number of members (10). */
    TRIP_FULL,

    /** No connection or the request failed; nothing changed. */
    FAILED,
}

/** No connection, a timeout, or the cloud refused the request; nothing changed. */
class InviteUnavailableException(cause: Throwable? = null) : Exception(cause)

/** The trip already has 10 members, so no invite can be made. */
class TripFullException : Exception()
