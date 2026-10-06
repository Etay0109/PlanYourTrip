package com.example.planyourtrip.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.planyourtrip.data.trip.ActivityType
import com.example.planyourtrip.data.trip.ExpenseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Activity type and expense category colors: readable, all different, never the budget-warning orange. */
class TintedColorsTest {

    /** WCAG 2 contrast ratio between two opaque colors (1..21). */
    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    private val schemes = mapOf("light" to LightExtendedColors, "dark" to DarkExtendedColors)

    /** Both palettes of one mode, labelled for failure messages. */
    private fun palettes(colors: ExtendedColors): Map<String, Map<out Any, TintedColor>> =
        mapOf("activity types" to colors.activityTypes, "categories" to colors.categories)

    @Test
    fun everyTypeAndCategoryHasColors_inBothModes() {
        schemes.values.forEach {
            assertEquals(ActivityType.entries.toSet(), it.activityTypes.keys)
            assertEquals(ExpenseCategory.entries.toSet(), it.categories.keys)
        }
    }

    @Test
    fun iconAndLabel_haveTextContrastOnTheirTint() {
        schemes.forEach { (mode, colors) ->
            palettes(colors).forEach { (palette, map) ->
                map.forEach { (key, color) ->
                    val ratio = contrast(color.content, color.container)
                    assertTrue("$palette $key in $mode: contrast $ratio < 4.5", ratio >= 4.5f)
                }
            }
        }
    }

    @Test
    fun colors_areAllDifferent_withinEachPalette_andNeverTheBudgetWarning() {
        schemes.forEach { (mode, colors) ->
            palettes(colors).forEach { (palette, map) ->
                val contents = map.values.map { it.content }
                val containers = map.values.map { it.container }
                assertEquals("$palette content colors repeat in $mode", contents.size, contents.toSet().size)
                assertEquals("$palette container colors repeat in $mode", containers.size, containers.toSet().size)
                assertFalse("$palette use orange in $mode", colors.budgetWarning in contents + containers)
            }
        }
    }

    @Test
    fun sharedMeanings_shareTheirHue() {
        schemes.values.forEach {
            assertEquals(it.activityType(ActivityType.DINING), it.category(ExpenseCategory.FOOD))
            assertEquals(it.activityType(ActivityType.LODGING), it.category(ExpenseCategory.HOTELS))
            assertEquals(it.activityType(ActivityType.TRANSPORT), it.category(ExpenseCategory.TRANSPORT))
            assertEquals(it.activityType(ActivityType.OTHER), it.category(ExpenseCategory.OTHER))
        }
    }

    @Test
    fun dotsAndSlices_standOutFromTheBackground() {
        // Timeline dots sit on the screen background; donut slices on the white (dark: night) card.
        schemes.forEach { (mode, colors) ->
            val background = if (mode == "light") WarmWhite else Night
            palettes(colors).forEach { (palette, map) ->
                map.forEach { (key, color) ->
                    val ratio = contrast(color.content, background)
                    assertTrue("$palette $key in $mode: contrast $ratio < 3 on the background", ratio >= 3f)
                }
            }
        }
    }
}
