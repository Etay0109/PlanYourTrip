package com.example.planyourtrip.ui.common.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.decode.DataSource
import com.example.planyourtrip.ui.theme.Dimens
import com.example.planyourtrip.ui.theme.ExtendedTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How long a downloaded photo takes to fade in (a photo from the memory cache shows at once). */
private const val PHOTO_FADE_MILLIS = 400

/**
 * The destination photo of a hero (Next trip card, trip header). [visibility] is 0 while there is no photo (no URL,
 * loading, failed: the hero looks exactly as without photos) and goes to 1 when it has loaded; the hero uses it for
 * its chip fills and text colors. [track] measures where text blocks sit, so the scrim can be strong only there.
 */
@Stable
class HeroPhotoState internal constructor(val url: String?) {
    internal val reveal = Animatable(0f)

    val visibility: Float get() = reveal.value

    /** [noPhoto] while there is no photo, [withPhoto] once it shows, blended while it fades in. */
    fun color(noPhoto: Color, withPhoto: Color): Color =
        if (visibility == 0f) noPhoto else lerp(noPhoto, withPhoto, visibility)

    private var heroCoordinates: LayoutCoordinates? = null
    private val textCoordinates = HashMap<String, LayoutCoordinates>()
    private val textBounds = mutableStateMapOf<String, Rect>()

    /** On the hero's outer box: the space the text bounds are measured in. */
    val heroModifier: Modifier = Modifier.onGloballyPositioned {
        heroCoordinates = it
        updateBounds()
    }

    /** On a text block: its bounds inside the hero become [boundsOf] ([name]). */
    fun track(name: String): Modifier = Modifier.onGloballyPositioned {
        textCoordinates[name] = it
        updateBounds()
    }

    /** Where the text block [name] sits inside the hero; null until it has been placed. */
    fun boundsOf(name: String): Rect? = textBounds[name]

    private fun updateBounds() {
        val hero = heroCoordinates?.takeIf { it.isAttached } ?: return
        for ((name, coordinates) in textCoordinates) {
            if (!coordinates.isAttached) continue
            val bounds = hero.localBoundingBoxOf(coordinates, clipBounds = false)
            // Relative bounds don't change while the list scrolls, so the scrim isn't redrawn for nothing.
            if (textBounds[name] != bounds) textBounds[name] = bounds
        }
    }
}

@Composable
fun rememberHeroPhotoState(url: String?): HeroPhotoState = remember(url) { HeroPhotoState(url) }

/**
 * The photo under a hero's content: cropped to fill, with [scrim] drawn over it, both fading in together once the
 * image has loaded. Draws nothing without a URL, while loading or on failure (no placeholder, spinner or error icon),
 * and never changes the hero's size (give it `matchParentSize()`).
 */
@Composable
fun HeroPhoto(
    state: HeroPhotoState,
    modifier: Modifier = Modifier,
    scrim: DrawScope.() -> Unit,
) {
    val url = state.url ?: return
    val scope = rememberCoroutineScope()
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { reveal(scope, state.reveal, it) },
        onError = { scope.launch { state.reveal.snapTo(0f) } },
        modifier = modifier
            .graphicsLayer { alpha = state.visibility }
            .drawWithContent {
                drawContent()
                scrim()
            },
    )
}

/**
 * The tint every hero photo gets, then [ExtendedColors.heroPhotoShade] at the given alphas along one axis:
 * [stops] are (position in px, alpha), in any order.
 */
fun DrawScope.drawHeroPhotoScrim(tint: Color, shade: Color, stops: List<Pair<Float, Float>>, vertical: Boolean) {
    drawRect(tint)
    val length = if (vertical) size.height else size.width
    if (length <= 0f || stops.isEmpty()) return
    val colorStops = stops.sortedBy { it.first }
        .map { (position, alpha) -> (position / length).coerceIn(0f, 1f) to shade.copy(alpha = alpha) }
        .toTypedArray()
    val brush = if (vertical) Brush.verticalGradient(*colorStops) else Brush.horizontalGradient(*colorStops)
    drawRect(brush)
}

/**
 * [LetterSquare] with the destination's photo thumbnail fading in over it once loaded: the same size and shape, so
 * nothing moves. Without a photo it is exactly the letter square.
 */
@Composable
fun PhotoSquare(
    url: String?,
    destination: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(Dimens.AvatarSize)) {
        LetterSquare(destination, container, content)
        if (url != null) {
            val reveal = remember(url) { Animatable(0f) }
            val scope = rememberCoroutineScope()
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { reveal(scope, reveal, it) },
                onError = { scope.launch { reveal.snapTo(0f) } },
                modifier = Modifier
                    .size(Dimens.AvatarSize)
                    .clip(MaterialTheme.shapes.medium)
                    .graphicsLayer { alpha = reveal.value },
            )
        }
    }
}

/** A photo from the memory cache shows at once (scrolling back, reopening a screen); a download fades in. */
private fun reveal(scope: CoroutineScope, reveal: Animatable<Float, *>, success: AsyncImagePainter.State.Success) {
    scope.launch {
        if (success.result.dataSource == DataSource.MEMORY_CACHE) {
            reveal.snapTo(1f)
        } else {
            reveal.animateTo(1f, tween(PHOTO_FADE_MILLIS))
        }
    }
}

/** The hero's muted text color: [ExtendedColors.onHeroMuted] without a photo, full white on one (more contrast). */
@Composable
fun HeroPhotoState.mutedTextColor(): Color =
    color(ExtendedTheme.colors.onHeroMuted, ExtendedTheme.colors.onHero)

/** Fill of the hero's countdown box, status chip and round buttons: darker on a photo. */
@Composable
fun HeroPhotoState.chipColor(): Color =
    color(ExtendedTheme.colors.heroOverlay, ExtendedTheme.colors.heroPhotoChip)
