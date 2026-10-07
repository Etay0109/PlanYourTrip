package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.ui.res.stringResource
import com.example.planyourtrip.ui.common.labelRes
import com.example.planyourtrip.data.trip.ActivityType
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.ui.common.iconRes
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme

/** Round gray chip with a teal icon (wallet, bookmark, plan) on the trip cards. */
@Composable
fun IconChip(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(Dimens.IconChipSize)
            .background(ExtendedTheme.colors.iconChip, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Round category icon: the category color on its light tint (expense rows and the chart legend). */
@Composable
fun CategoryIcon(category: ExpenseCategory, modifier: Modifier = Modifier) {
    val color = ExtendedTheme.colors.category(category)
    Box(
        modifier = modifier
            .size(Dimens.CategoryIconSize)
            .background(color.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(category.iconRes()),
            contentDescription = null,
            tint = color.content,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Round activity type icon: the type's hue on its light tint (activity lists outside the timeline). */
@Composable
fun ActivityTypeIcon(type: ActivityType, modifier: Modifier = Modifier) {
    val color = ExtendedTheme.colors.activityType(type)
    Box(
        modifier = modifier
            .size(Dimens.CategoryIconSize)
            .background(color.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(type.iconRes()),
            contentDescription = stringResource(type.labelRes()),
            tint = color.content,
            modifier = Modifier.size(20.dp),
        )
    }
}
