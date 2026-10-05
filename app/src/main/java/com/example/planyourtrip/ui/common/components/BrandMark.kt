package com.example.planyourtrip.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.ExtendedTheme

/** The app's brand mark: a white plane on the teal hero gradient, in a circle. */
@Composable
fun BrandMark(size: Dp, iconSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .background(ExtendedTheme.colors.heroGradient, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_flight),
            contentDescription = null,
            tint = ExtendedTheme.colors.onHero,
            modifier = Modifier.size(iconSize),
        )
    }
}
