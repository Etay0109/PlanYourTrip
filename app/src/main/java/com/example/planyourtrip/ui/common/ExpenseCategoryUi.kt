package com.example.planyourtrip.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.ExpenseCategory

@DrawableRes
fun ExpenseCategory.iconRes(): Int = when (this) {
    ExpenseCategory.FLIGHTS -> R.drawable.ic_flight
    ExpenseCategory.HOTELS -> R.drawable.ic_hotel
    ExpenseCategory.FOOD -> R.drawable.ic_restaurant
    ExpenseCategory.SHOPPING -> R.drawable.ic_shopping_bag
    ExpenseCategory.TRANSPORT -> R.drawable.ic_bus
    ExpenseCategory.ATTRACTIONS -> R.drawable.ic_ticket
    ExpenseCategory.OTHER -> R.drawable.ic_more
}

@StringRes
fun ExpenseCategory.labelRes(): Int = when (this) {
    ExpenseCategory.FLIGHTS -> R.string.category_flights
    ExpenseCategory.HOTELS -> R.string.category_hotels
    ExpenseCategory.FOOD -> R.string.category_food
    ExpenseCategory.SHOPPING -> R.string.category_shopping
    ExpenseCategory.TRANSPORT -> R.string.category_transport
    ExpenseCategory.ATTRACTIONS -> R.string.category_attractions
    ExpenseCategory.OTHER -> R.string.category_other
}
