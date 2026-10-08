package com.example.planyourtrip.ui.common.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.planyourtrip.ui.theme.TintedColor

/**
 * A choice chip in its own [color] (expense category, activity type): the icon always, the tinted fill and border
 * when selected.
 */
@Composable
fun TintedFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    @DrawableRes icon: Int,
    color: TintedColor,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(18.dp))
        },
        colors = FilterChipDefaults.filterChipColors(
            iconColor = color.content,
            selectedContainerColor = color.container,
            selectedLabelColor = color.content,
            selectedLeadingIconColor = color.content,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = color.content,
            selectedBorderWidth = 1.dp,
        ),
    )
}
