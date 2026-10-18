package com.example.planyourtrip.ui.common

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.Serializable

/**
 * A form's UI state whose draft (what the user entered and chose) is also kept in a [SavedStateHandle], so the form
 * comes back as it was after process death. Every [update] writes [toDraft] of the new state; null keeps nothing (a
 * closed dialog, or an edited item still loading). [restored] is the draft saved before the process died, if any.
 *
 * The fields' text itself is Compose state ([androidx.compose.runtime.saveable.rememberSaveable]); the copy here is
 * what the ViewModel validates and saves, and is never fed back into a text field while typing.
 */
class SavedFormState<S, D : Serializable>(
    initial: S,
    /** Null (tests) keeps nothing. */
    private val savedState: SavedStateHandle?,
    private val key: String,
    private val toDraft: (S) -> D?,
) {
    val restored: D? = savedState?.get<D>(key)

    private val state = MutableStateFlow(initial)
    val flow: StateFlow<S> = state.asStateFlow()
    val value: S get() = state.value

    fun update(transform: (S) -> S) {
        state.update(transform)
        savedState?.set(key, toDraft(state.value))
    }
}
