package com.example.planyourtrip.data.trip

/**
 * The longest text the app stores and syncs, per field. firestore.rules rejects anything longer (see `validTrip`,
 * `validExpense`, `validActivity`, `validChecklistItem`, `validTraveler`), so these must match the rules: the input
 * fields stop here, the repositories cut longer text (e.g. an assistant suggestion), and so does the upload
 * (SyncDocuments), so a sync is never refused for it.
 */
object TextLimits {
    const val DESTINATION = 200
    const val EXPENSE_NAME = 200
    const val ACTIVITY_TITLE = 200
    const val ACTIVITY_LOCATION = 300
    const val ACTIVITY_NOTE = 2000
    const val CHECKLIST_TEXT = 500
    const val TRAVELER_NAME = 100
}

/**
 * At most [max] characters (UTF-16 units, so never more characters than the rules count). Never cuts an emoji or
 * other surrogate pair in half.
 */
fun String.limitTo(max: Int): String {
    if (length <= max) return this
    val end = if (max > 0 && this[max - 1].isHighSurrogate()) max - 1 else max
    return substring(0, end)
}
