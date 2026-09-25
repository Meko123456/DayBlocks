package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.common.formatDuration
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock

/** A notification's two lines: who is speaking, and what they say. */
data class Line(val title: String, val body: String)

/**
 * What the buddy says in each situation. [NotificationPlanner] decides *when* something is said;
 * this decides the words, so the two can change independently.
 */
interface BuddyVoice {
    /** [clock] is the start as the user's own clock reads it; [minutes] is how long it really lasts. */
    fun blockStart(block: TimeBlock, clock: String, minutes: Int): Line
    fun checkIn(block: TimeBlock, minutesLeft: Int): Line
    fun backOnTrack(block: TimeBlock, minutesLeft: Int): Line
    fun planTomorrow(): Line
    fun planToday(): Line
    fun reviewDay(): Line
}

/**
 * One line per situation — the spec's own examples. The buddy engine replaces it with a pool of
 * lines per situation, rotated and in the chosen tone; the planner does not change when it does.
 */
class PlainVoice(private val name: String = DEFAULT_BUDDY_NAME) : BuddyVoice {
    override fun blockStart(block: TimeBlock, clock: String, minutes: Int) =
        Line(name, "Hey! It's $clock. Time for ${block.quoted} ${block.category.emoji} You've got ${spokenLength(minutes)}.")

    override fun checkIn(block: TimeBlock, minutesLeft: Int) =
        Line(name, "Still on ${block.quoted}? Or did the phone win again? 👀")

    override fun backOnTrack(block: TimeBlock, minutesLeft: Int) =
        Line(name, "No stress, ${formatDuration(minutesLeft)} left. Let's go back to it.")

    override fun planTomorrow() = Line(name, "Tomorrow's still empty. Want to plan it in 2 minutes?")

    override fun planToday() = Line(name, "Today's still a blank page. Plan just one block?")

    override fun reviewDay() = Line(name, "How did today go? Tap to review.")
}

/** The buddy's name until the user picks one. */
const val DEFAULT_BUDDY_NAME: String = "Kubi"

/** The emoji a line about this category ends on. */
val Category.emoji: String
    get() = when (this) {
        Category.Work -> "💼"
        Category.Rest -> "☕"
        Category.Reading -> "📖"
        Category.Exercise -> "🏃"
        Category.Cooking -> "🍳"
        Category.Sleep -> "😴"
        Category.Personal -> "🌱"
        Category.Other -> "✨"
    }

/** "an hour", "2 hours", "45 minutes", "1h 20m": how long a block lasts, said aloud. */
internal fun spokenLength(minutes: Int): String = when {
    minutes == 1 -> "a minute"
    minutes == 60 -> "an hour"
    minutes < 60 -> "$minutes minutes"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> formatDuration(minutes)
}

/** The title in quotes, so any phrasing reads as a name: Time for “Read Dune”. */
private val TimeBlock.quoted: String get() = "“$title”"
