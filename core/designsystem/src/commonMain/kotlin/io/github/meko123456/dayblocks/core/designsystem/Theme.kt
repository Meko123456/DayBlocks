package io.github.meko123456.dayblocks.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.meko123456.dayblocks.core.domain.model.Category

private val Sunrise = Color(0xFFF2994A)
private val Ink = Color(0xFF1B1D22)
private val Paper = Color(0xFFFAF7F2)

private val LightScheme = lightColorScheme(
    primary = Sunrise,
    onPrimary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
)

private val DarkScheme = darkColorScheme(
    primary = Sunrise,
    onPrimary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = Color(0xFF24262C),
    onSurface = Paper,
)

/**
 * [categoryColors] are the user's choices from Settings; a category without one keeps its
 * default. Provided here, at the root, so every screen reads the same colour for "Work" through
 * [color] and none of them has to know where the choice is stored.
 */
@Composable
fun DayBlocksTheme(darkTheme: Boolean, categoryColors: Map<Category, Color> = emptyMap(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCategoryColors provides categoryColors) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
    }
}

/** The user's colour choices, as [DayBlocksTheme] provides them. */
val LocalCategoryColors = staticCompositionLocalOf<Map<Category, Color>> { emptyMap() }

/** This category's colour: the user's choice where there is one, the default otherwise. */
val Category.color: Color
    @Composable get() = LocalCategoryColors.current[this] ?: defaultColor

/** What Settings offers for a category's colour: the defaults, and a few more. */
val CategoryPalette: List<Color> = listOf(
    Color(0xFF4A7CF2), Color(0xFF2D9CDB), Color(0xFF2BB5A0), Color(0xFF6FCF97), Color(0xFFF2C94C),
    Color(0xFFF2994A), Color(0xFFEB5757), Color(0xFFE86FA8), Color(0xFF9B51E0), Color(0xFF56508C), Color(0xFF828282),
)

/**
 * The colour of each category, in one place.
 *
 * Deliberately not stored on the [Category] enum: colour is a presentation concern, and putting it
 * there would put a Compose type in the domain layer — which is exactly the import the domain
 * module's dependency list exists to prevent.
 *
 * Settings can override these per category, through [color]; this is the default set.
 */
val Category.defaultColor: Color
    get() = when (this) {
        Category.Work -> Color(0xFF4A7CF2)
        Category.Rest -> Color(0xFF6FCF97)
        Category.Reading -> Color(0xFF9B51E0)
        Category.Exercise -> Color(0xFFEB5757)
        Category.Cooking -> Color(0xFFF2C94C)
        Category.Sleep -> Color(0xFF56508C)
        Category.Personal -> Color(0xFF2D9CDB)
        Category.Other -> Color(0xFF828282)
    }
