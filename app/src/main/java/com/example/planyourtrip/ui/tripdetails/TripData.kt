package com.example.planyourtrip.ui.tripdetails

import com.example.planyourtrip.data.auth.AuthRepository
import com.example.planyourtrip.data.trip.ActivityRepository
import com.example.planyourtrip.data.trip.Expense
import com.example.planyourtrip.data.trip.ExpenseRepository
import com.example.planyourtrip.data.trip.Traveler
import com.example.planyourtrip.data.trip.TravelerRepository
import com.example.planyourtrip.data.trip.Trip
import com.example.planyourtrip.data.trip.TripActivity
import com.example.planyourtrip.data.trip.TripMember
import com.example.planyourtrip.data.trip.TripMemberRepository
import com.example.planyourtrip.data.trip.TripRepository
import com.example.planyourtrip.ui.common.WhileUiSubscribed
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn

/** Everything the trip screen shows about one trip of the signed-in account ([uid]), as Room has it. */
data class TripData(
    val uid: String,
    val trip: Trip,
    val expenses: List<Expense>,
    val travelers: List<Traveler>,
    val members: List<TripMember>,
    val activities: List<TripActivity>,
)

/**
 * The trip's data, observed once and shared by the screen's parts (budget, itinerary, currency card, header photo).
 * Null when signed out or when the trip does not exist (deleted) or belongs to another account. Nothing is emitted
 * until Room has answered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun observeTripData(
    tripId: Long,
    authRepository: AuthRepository,
    tripRepository: TripRepository,
    expenseRepository: ExpenseRepository,
    travelerRepository: TravelerRepository,
    memberRepository: TripMemberRepository,
    activityRepository: ActivityRepository,
    computeDispatcher: CoroutineDispatcher,
    scope: CoroutineScope,
): SharedFlow<TripData?> = authRepository.currentUser
    .flatMapLatest { user ->
        if (user == null) return@flatMapLatest flowOf(null)
        combine(
            tripRepository.observeTrip(tripId, user.uid),
            expenseRepository.observeExpenses(tripId, user.uid),
            travelerRepository.observeTravelers(tripId, user.uid),
            memberRepository.observeMembers(tripId, user.uid),
            activityRepository.observeActivities(tripId, user.uid),
        ) { trip, expenses, travelers, members, activities ->
            trip?.let { TripData(user.uid, it, expenses, travelers, members, activities) }
        }
    }
    .flowOn(computeDispatcher)
    .shareIn(scope, WhileUiSubscribed, replay = 1)
