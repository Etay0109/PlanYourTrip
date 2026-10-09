package com.example.planyourtrip.ui.tripdetails.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.common.split.Person
import com.example.planyourtrip.ui.common.split.avatarLetter
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.TintedColor
import com.example.planyourtrip.ui.theme.travelerAvatarTextStyle

/**
 * A round avatar with the person's initials: "YOU" on the brand teal, one letter on the traveler's color.
 * Decorative: callers always show or announce the name next to it.
 */
@Composable
fun TravelerAvatar(person: Person, size: Dp, modifier: Modifier = Modifier) {
    val colors = avatarColors(person)
    val text = when (person) {
        is Person.You -> stringResource(R.string.traveler_you_initials)
        is Person.Named -> avatarLetter(person.name)
        is Person.Removed -> "?"
    }
    Box(
        modifier = modifier
            .size(size)
            .background(colors.container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = travelerAvatarTextStyle(size, text.length, LocalDensity.current.fontScale),
            color = colors.content,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** Fill ([TintedColor.container]) and initials color ([TintedColor.content]) of [person]'s avatar. */
@Composable
@ReadOnlyComposable
fun avatarColors(person: Person): TintedColor = when (person) {
    is Person.You -> TintedColor(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
    is Person.Named -> ExtendedTheme.colors.traveler(person.color)
    is Person.Removed -> ExtendedTheme.colors.removedTraveler
}

/** The name shown for [person]: "You", the traveler's name, or "Removed traveler". */
@Composable
fun personName(person: Person): String = when (person) {
    is Person.You -> stringResource(R.string.traveler_you)
    is Person.Named -> person.name
    is Person.Removed -> stringResource(R.string.traveler_removed)
}
