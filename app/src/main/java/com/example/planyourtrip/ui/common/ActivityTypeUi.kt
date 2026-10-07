package com.example.planyourtrip.ui.common

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.planyourtrip.R
import com.example.planyourtrip.data.trip.ActivityType

@DrawableRes
fun ActivityType.iconRes(): Int = when (this) {
    ActivityType.ACTIVITY -> R.drawable.ic_museum
    ActivityType.DINING -> R.drawable.ic_restaurant
    ActivityType.TRANSPORT -> R.drawable.ic_bus
    ActivityType.LODGING -> R.drawable.ic_hotel
    ActivityType.OTHER -> R.drawable.ic_more
    ActivityType.SHOPPING -> R.drawable.ic_shopping_bag
}

@StringRes
fun ActivityType.labelRes(): Int = when (this) {
    ActivityType.ACTIVITY -> R.string.activity_type_activity
    ActivityType.DINING -> R.string.activity_type_dining
    ActivityType.TRANSPORT -> R.string.activity_type_transport
    ActivityType.LODGING -> R.string.activity_type_lodging
    ActivityType.OTHER -> R.string.activity_type_other
    ActivityType.SHOPPING -> R.string.activity_type_shopping
}
