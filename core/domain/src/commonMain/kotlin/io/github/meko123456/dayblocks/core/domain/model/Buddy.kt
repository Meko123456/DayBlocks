package io.github.meko123456.dayblocks.core.domain.model

import kotlinx.datetime.LocalTime

/**
 * How the buddy is feeling about the day so far. Chosen by the buddy engine from adherence and the
 * hour, drawn by the design system. One choice, so the Today screen, the widgets and anything else
 * that shows the buddy can never disagree about its face.
 */
enum class BuddyMood { Happy, Proud, Encouraging, Worried, Disappointed, Sleepy }

/** How hard the buddy pushes: which lines it picks, and how often it is allowed to speak. */
enum class BuddyTone { Gentle, Normal, Pushy }

/**
 * The hours the buddy stays silent. [start] to [end], wrapping past midnight when [start] is
 * later than [end] — 23:00 to 08:00 is the default. Equal ends mean no quiet hours at all.
 */
data class QuietHours(
    val start: LocalTime = LocalTime(23, 0),
    val end: LocalTime = LocalTime(8, 0),
) {
    operator fun contains(time: LocalTime): Boolean =
        if (start <= end) time >= start && time < end else time >= start || time < end
}

/** Everything the user decides about the buddy. */
data class BuddySettings(
    val name: String = DEFAULT_NAME,
    val tone: BuddyTone = BuddyTone.Normal,
    val quietHours: QuietHours = QuietHours(),
    /** The most notifications in one planning day, whatever the plan asks for. */
    val dailyCap: Int = DEFAULT_DAILY_CAP,
) {
    init {
        require(name.isNotBlank()) { "the buddy needs a name" }
        require(name.length <= MAX_NAME_LENGTH) { "a name of at most $MAX_NAME_LENGTH characters" }
        require(dailyCap in 1..MAX_DAILY_CAP) { "a daily cap between 1 and $MAX_DAILY_CAP" }
    }

    companion object {
        const val DEFAULT_NAME: String = "Kubi"
        const val MAX_NAME_LENGTH: Int = 20
        const val DEFAULT_DAILY_CAP: Int = 12
        const val MAX_DAILY_CAP: Int = 40
    }
}
