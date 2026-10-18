package com.example.planyourtrip.ui.common.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.IconSize
import com.example.planyourtrip.ui.theme.Spacing

/**
 * Pill-shaped primary button. While [loading], it is disabled and shows a spinner instead of [text].
 * [enabled] = false disables it without the spinner (e.g. while another request on the screen runs).
 * [leadingIcon] is an optional drawable shown before the text (e.g. "+ Add expense").
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = CircleShape,
        modifier = modifier.height(Dimens.ButtonHeight),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(IconSize.l), strokeWidth = Dimens.ProgressStroke)
        } else {
            if (leadingIcon != null) {
                Icon(painterResource(leadingIcon), contentDescription = null, modifier = Modifier.size(IconSize.m))
                Spacer(Modifier.width(Spacing.s))
            }
            Text(text)
        }
    }
}
