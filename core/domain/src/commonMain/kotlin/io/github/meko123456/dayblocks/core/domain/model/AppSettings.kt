package io.github.meko123456.dayblocks.core.domain.model

/** Light, dark, or whatever the device is set to. */
enum class ThemeMode { System, Light, Dark }

/** Everything the user decides about the app itself, as opposed to the buddy. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    /** The hours Today's timeline covers when nothing planned asks for more. */
    val timelineStartHour: Int = DEFAULT_TIMELINE_START,
    val timelineEndHour: Int = DEFAULT_TIMELINE_END,
    /** A colour chosen per category, as ARGB. A category without one keeps its default. */
    val categoryColors: Map<Category, Long> = emptyMap(),
    /** Whether the user has been through onboarding, or never needed it. */
    val onboarded: Boolean = false,
) {
    init {
        require(timelineStartHour in 0..23 && timelineEndHour in 1..24 && timelineStartHour < timelineEndHour) {
            "the timeline runs forward within a day, $timelineStartHour to $timelineEndHour is not that"
        }
    }

    companion object {
        const val DEFAULT_TIMELINE_START: Int = 6
        const val DEFAULT_TIMELINE_END: Int = 24
    }
}
