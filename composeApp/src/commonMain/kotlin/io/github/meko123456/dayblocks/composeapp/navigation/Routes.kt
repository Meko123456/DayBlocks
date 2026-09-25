package io.github.meko123456.dayblocks.composeapp.navigation

/**
 * Every destination in the app, in one place.
 *
 * This is the reason feature modules do not depend on each other: a screen raises a navigation
 * Effect, and only the graph below decides what that means. :feature:today can send you to
 * :feature:editblock without knowing it exists.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val TODAY = "today"
    const val EDIT_BLOCK = "editblock"
    const val TEMPLATES = "templates"
    const val CHECK_IN = "checkin"
    const val STATS = "stats"
    const val SETTINGS = "settings"
}
