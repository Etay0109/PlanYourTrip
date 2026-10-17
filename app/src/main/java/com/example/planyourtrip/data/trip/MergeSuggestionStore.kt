package com.example.planyourtrip.data.trip

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The "Merge with the traveler?" suggestions the user answered "Keep separate", on this device only (per account,
 * trip, traveler and member), so each one is offered once. Never synced. Tests use a fake.
 */
interface MergeSuggestionStore {
    /** Keys of the dismissed suggestions ([key]). */
    val dismissed: StateFlow<Set<String>>

    fun dismiss(key: String)

    companion object {
        fun key(ownerId: String, tripUuid: String, travelerUuid: String, memberUid: String) =
            "$ownerId|$tripUuid|$travelerUuid|$memberUid"
    }
}

/** Remembers the answers while the process lives (tests, and ViewModels created without a store). */
class InMemoryMergeSuggestionStore : MergeSuggestionStore {
    private val keys = MutableStateFlow(emptySet<String>())

    override val dismissed: StateFlow<Set<String>> = keys.asStateFlow()

    override fun dismiss(key: String) {
        keys.value = keys.value + key
    }
}

/** SharedPreferences "merge_suggestions", one string set `dismissed`. */
class SharedPreferencesMergeSuggestionStore(private val prefs: SharedPreferences) : MergeSuggestionStore {
    private val keys = MutableStateFlow(prefs.getStringSet(DISMISSED, emptySet()).orEmpty().toSet())

    override val dismissed: StateFlow<Set<String>> = keys.asStateFlow()

    override fun dismiss(key: String) {
        keys.value = keys.value + key
        prefs.edit { putStringSet(DISMISSED, keys.value) }
    }

    private companion object {
        const val DISMISSED = "dismissed"
    }
}
