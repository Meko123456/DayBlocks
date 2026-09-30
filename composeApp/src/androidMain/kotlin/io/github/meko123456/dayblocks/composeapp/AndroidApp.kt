package io.github.meko123456.dayblocks.composeapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [App] as the Android activity shows it: laid out above the keyboard.
 *
 * Edge to edge, the window is not resized for the keyboard: `adjustResize` only hands the app the
 * keyboard's insets. Every screen went on laying itself out behind the keys, so a focused field
 * could sit under them with no way to see what was typed: onboarding's name on a small phone, a
 * block's note. Padding the root by the keyboard gives back what `adjustResize` used to do. iOS
 * needs no counterpart; Compose there lifts the focused field itself, which is its default
 * `OnFocusBehavior.FocusableAboveKeyboard`.
 */
@Composable
fun AndroidApp() {
    Box(Modifier.imePadding()) { App() }
}
