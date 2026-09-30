package io.github.meko123456.dayblocks.core.designsystem

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
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

/**
 * Sunrise, darker, for orange *text* in the light theme. Sunrise itself is 2.1:1 on Paper, under
 * the 4.5:1 that text needs, so the "Check-in", "Apply to today" and every other orange label
 * could be seen but not comfortably read. Ember is 5.3:1 or better on every light surface and
 * container below. Fills keep Sunrise: that is the brand, and a fill is not read.
 */
private val Ember = Color(0xFF8F490A)

/*
 * Every role a Material component reads by default is named here, not only the six the screens
 * ask for by name. The rest used to fall through to Material's baseline, which is lavender: the
 * first screenshot tests drew Today's "Add block" button (primaryContainer) and the template cards
 * (surfaceContainerHighest) in it. The containers are Sunrise and Paper mixed, the surfaces warm
 * steps up from Paper, and the neutral text colours on them read at 7:1 or better in both themes.
 * Orange text is [LocalAccentText]'s, not primary's.
 */
private val LightScheme = lightColorScheme(
    primary = Sunrise,
    // Ink, not white: white on Sunrise is 2.2:1, Ink is 7.6:1. The fill stays the brand orange.
    onPrimary = Ink,
    primaryContainer = Color(0xFFFBE0C9),
    onPrimaryContainer = Color(0xFF523419),
    secondaryContainer = Color(0xFFF9E8D7),
    onSecondaryContainer = Color(0xFF3A2A1C),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF9ECDE),
    onSurfaceVariant = Color(0xFF51463C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF4ED),
    surfaceContainer = Color(0xFFFAF1E8),
    surfaceContainerHigh = Color(0xFFF9EFE3),
    surfaceContainerHighest = Color(0xFFF9ECDE),
    outline = Color(0xFF85776A),
    outlineVariant = Color(0xFFD8CCBE),
)

private val DarkScheme = darkColorScheme(
    primary = Sunrise,
    onPrimary = Ink,
    primaryContainer = Color(0xFF66401F),
    onPrimaryContainer = Color(0xFFFBE2CC),
    secondaryContainer = Color(0xFF46362A),
    onSecondaryContainer = Color(0xFFF6E6D6),
    background = Ink,
    onBackground = Paper,
    surface = Color(0xFF24262C),
    onSurface = Paper,
    surfaceVariant = Color(0xFF3A3637),
    onSurfaceVariant = Color(0xFFD9CDBF),
    surfaceContainerLowest = Color(0xFF15171B),
    surfaceContainerLow = Color(0xFF1F2126),
    surfaceContainer = Color(0xFF24262C),
    surfaceContainerHigh = Color(0xFF2C2B2D),
    surfaceContainerHighest = Color(0xFF383537),
    outline = Color(0xFFA08F7E),
    outlineVariant = Color(0xFF4E4439),
)

/**
 * [categoryColors] are the user's choices from Settings; a category without one keeps its
 * default. Provided here, at the root, so every screen reads the same colour for "Work" through
 * [color] and none of them has to know where the choice is stored.
 */
@Composable
fun DayBlocksTheme(darkTheme: Boolean, categoryColors: Map<Category, Color> = emptyMap(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalCategoryColors provides categoryColors,
        LocalAccentText provides if (darkTheme) Sunrise else Ember,
    ) {
        MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme, content = content)
    }
}

/**
 * The orange to write in, where the scheme's primary is the orange to fill with. Material has one
 * role for both, since a text button's label and a filled button's background are each `primary`,
 * and Sunrise can only be one of them on a light surface. Dark surfaces keep Sunrise, which reads
 * at 5.2:1 or better on every one of them.
 */
val LocalAccentText = staticCompositionLocalOf { Ember }

/** A text button with its label in [LocalAccentText] rather than primary. */
@Composable
fun accentTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(contentColor = LocalAccentText.current)

/** An outlined text field whose focused label is [LocalAccentText] rather than primary. */
@Composable
fun accentOutlinedTextFieldColors(): TextFieldColors =
    OutlinedTextFieldDefaults.colors(focusedLabelColor = LocalAccentText.current)

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
