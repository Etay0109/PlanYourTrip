package com.example.planyourtrip.ui.tripdetails.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.data.trip.ExpenseCategory
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.tripdetails.iconRes

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

/** Rounded square with the category icon, tinted with the category's chart color. */
@Composable
fun CategoryIcon(category: ExpenseCategory, modifier: Modifier = Modifier) {
    val color: Color = ExtendedTheme.colors.category(category)
    Box(
        modifier = modifier
            .size(Dimens.CategoryIconSize)
            .background(color.copy(alpha = 0.16f), MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(category.iconRes()),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
    }
}
