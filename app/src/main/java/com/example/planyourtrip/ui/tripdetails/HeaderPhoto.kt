package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.data.photo.DestinationPhoto
import com.example.planyourtrip.data.photo.DestinationPhotoRepository
import com.example.planyourtrip.data.photo.photoQuery
import com.example.planyourtrip.ui.common.WhileUiSubscribed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The header's destination photo (this device only); null while there is none (the gradient only). An edited
 * destination is a new lookup, so its photo replaces the old one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun headerPhoto(
    tripData: Flow<TripData?>,
    photoRepository: DestinationPhotoRepository,
    scope: CoroutineScope,
): StateFlow<DestinationPhoto?> = tripData
    .map { it?.trip?.photoQuery }
    .distinctUntilChanged()
    .flatMapLatest { query ->
        if (query == null) return@flatMapLatest flowOf(null)
        photoRepository.request(listOf(query))
        photoRepository.observe(setOf(query.key)).map { it[query.key] }
    }
    .stateIn(scope, WhileUiSubscribed, null)
