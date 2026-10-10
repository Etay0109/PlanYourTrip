package com.example.planyourtrip.data.trip

/**
 * A member of a shared trip as its member document in the cloud describes them (trips/{trip}/members/{uid}): the
 * display name they joined with (never an email), when they joined, and whether they left or were removed. Who is a
 * member right now is the trip's [Trip.memberIds]; a member of a one-member trip may have no document at all.
 * Downloaded by the full sync only; never edited on the device.
 */
data class TripMember(
    val uid: String,
    val name: String,
    /** Epoch millis of when they joined (server time), or null when unknown. */
    val joinedAt: Long?,
    /** True after they left or were removed; their name still shows on old expenses. */
    val left: Boolean,
)
