package com.example.planyourtrip.data.sync

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * True while the app is in the foreground: one of its activities is started (the process lifecycle, which ignores
 * rotations and quick activity changes). False while Android runs the process in the background only, e.g. after
 * boot, for an alarm or for WorkManager. One instance per process.
 */
fun processInForeground(scope: CoroutineScope): StateFlow<Boolean> =
    ProcessLifecycleOwner.get().lifecycle.currentStateFlow
        .map { it.isAtLeast(Lifecycle.State.STARTED) }
        .stateIn(scope, SharingStarted.Eagerly, false)
