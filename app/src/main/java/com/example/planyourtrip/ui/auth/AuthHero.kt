package com.example.planyourtrip.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.planyourtrip.R
import com.example.planyourtrip.ui.theme.AuthHeadlineStyle
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import com.example.planyourtrip.ui.theme.Spacing

/**
 * Fixed behind the status bar (it doesn't scroll): dark teal at 40% under the clock and icons, fading out just below
 * them, so they stay readable over a bright sky and over the hero's content when it scrolls up under them.
 */
@Composable
internal fun StatusBarScrim() {
    val color = ExtendedTheme.colors.authStatusBarScrim
    val statusBar = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }
    val height = statusBar + Dimens.AuthStatusBarFade
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to color,
                    statusBar / height to color.copy(alpha = color.alpha * 0.75f),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/** The hero's photo, [height] tall: a fixed layer under the scrolling [AuthHeroContent]. */
@Composable
internal fun AuthHeroPhoto(isSignUp: Boolean, height: Dp) {
    Box(Modifier.fillMaxWidth().height(height)) {
        Image(
            painter = painterResource(if (isSignUp) R.drawable.auth_hero_sign_up else R.drawable.auth_hero_sign_in),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
    }
}

/**
 * Gradient, app icon, headline and subtitle over the photo. The gradient scrolls with the text, so the text keeps its
 * dark backing when it moves up over a bright part of the photo. Grows past [minHeight] when its text needs more room.
 */
@Composable
internal fun AuthHeroContent(isSignUp: Boolean, minHeight: Dp, modifier: Modifier = Modifier) {
    val colors = ExtendedTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.authHeroScrim))),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = Spacing.xxl, end = Spacing.xxl, top = Spacing.xxl, bottom = Dimens.AuthSheetCorner + Spacing.xxlPlus),
        ) {
            AuthAppIcon()
            Spacer(Modifier.height(Spacing.xl))
            Text(
                text = stringResource(if (isSignUp) R.string.auth_headline_sign_up else R.string.auth_headline_sign_in),
                style = AuthHeadlineStyle,
                color = colors.onAuthHero,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.s))
            Text(
                text = stringResource(if (isSignUp) R.string.auth_subtitle_sign_up else R.string.auth_subtitle_sign_in),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onAuthHeroMuted,
            )
        }
    }
}

/**
 * The real launcher icon: its background color and foreground (pin + airplane). The foreground is drawn at the
 * adaptive icon's 108/72 scale, so the visible 72dp part fills the square like on the home screen.
 */
@Composable
private fun AuthAppIcon() {
    val shape = MaterialTheme.shapes.large
    Box(
        modifier = Modifier
            .size(Dimens.AuthAppIconSize)
            .shadow(elevation = AppIconElevation, shape = shape)
            .clip(shape)
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(Dimens.AuthAppIconSize * (108f / 72f)),
        )
    }
}

/** White status bar icons over the hero photo while the screen shows; the previous setting comes back after. */
@Composable
internal fun WhiteStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** The app icon's shadow on the hero. */
private val AppIconElevation = 10.dp
