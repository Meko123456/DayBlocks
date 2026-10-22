package io.github.meko123456.dayblocks.composeapp.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.LocalDate

/** Somewhere the app was asked to open from outside itself: a notification, a widget, its icon. */
sealed interface AppLink {
    /** The review notification: that day's check-in. */
    data class Review(val date: LocalDate?) : AppLink

    /** A widget: Today, whatever screen the app was left on. */
    data object Today : AppLink

    /**
     * Add a block from the app's icon (the launcher shortcut on Android, the Home Screen quick
     * action on iOS): the block editor, as + on Today opens it.
     */
    data object NewBlock : AppLink
}

/**
 * The hand-off between a platform's "the user tapped this" and the navigation graph. The tap can
 * arrive before the graph exists — it is what launched the app — so the link waits here until
 * the graph takes it.
 */
object AppLinks {
    private val pending = MutableStateFlow<AppLink?>(null)
    val next: StateFlow<AppLink?> = pending.asStateFlow()

    fun open(link: AppLink) {
        pending.value = link
    }

    fun consumed() {
        pending.value = null
    }
}
