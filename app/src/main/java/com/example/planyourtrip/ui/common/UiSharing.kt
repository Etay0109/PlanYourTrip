package com.example.planyourtrip.ui.common

import kotlinx.coroutines.flow.SharingStarted

/**
 * How ViewModels share their UI state flows: kept alive 5 seconds after the last collector leaves, so a configuration
 * change (rotation) doesn't restart the Room queries.
 */
val WhileUiSubscribed: SharingStarted = SharingStarted.WhileSubscribed(5_000)
