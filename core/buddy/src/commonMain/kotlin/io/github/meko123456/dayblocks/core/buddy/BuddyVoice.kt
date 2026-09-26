package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.common.formatDuration
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category

/** A notification's two lines: who is speaking, and what they say. */
data class Line(val title: String, val body: String)

/** The values a template's placeholders are filled from. Only a situation's own are set. */
data class Slots(
    val title: String? = null,
    val emoji: String? = null,
    val time: String? = null,
    val length: String? = null,
    val left: String? = null,
    val streak: Int? = null,
    val next: String? = null,
    val nextTime: String? = null,
)

/**
 * Picks a line and fills it in. Which line is decided by [rotation], so the choice is the caller's
 * to make stable: the planner numbers each situation's occurrences through the day, and Today uses
 * the block on screen, so the same notification keeps its words and back-to-back ones differ.
 */
class BuddyVoice(private val pools: MessagePools = MessagePools.Default) {

    fun line(situation: Situation, tone: BuddyTone, name: String, rotation: Int, slots: Slots): Line =
        Line(title = name, body = pools.lines(situation, tone).pick(rotation).fill(slots, name))

    fun bubble(bubble: Bubble, name: String, rotation: Int, slots: Slots): String =
        pools.lines(bubble).pick(rotation).fill(slots, name)
}

private fun List<String>.pick(rotation: Int): String = this[rotation.mod(size)]

internal fun String.fill(slots: Slots, name: String): String = this
    .replace("{name}", name)
    .replace("{title}", slots.title?.let { "“$it”" }.orEmpty())
    .replace("{emoji}", slots.emoji.orEmpty())
    .replace("{time}", slots.time.orEmpty())
    .replace("{length}", slots.length.orEmpty())
    .replace("{left}", slots.left.orEmpty())
    .replace("{streakNext}", slots.streak?.plus(1)?.toString().orEmpty())
    .replace("{streak}", slots.streak?.toString().orEmpty())
    .replace("{next}", slots.next?.let { "“$it”" }.orEmpty())
    .replace("{nextTime}", slots.nextTime.orEmpty())

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

/**
 * FNV-1a over the characters. Here rather than `hashCode()`, which Kotlin does not promise to be
 * the same on every platform — and a line that differed between an iPhone and an Android phone
 * for the same plan would be a bug nobody could reproduce.
 */
internal fun stableHash(text: String): Int {
    var hash = 0x811C9DC5.toInt()
    for (c in text) {
        hash = hash xor c.code
        hash *= 0x01000193
    }
    return hash and Int.MAX_VALUE
}
