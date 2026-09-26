package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** The Today screen at this moment, as far as the buddy is concerned. */
data class TodaySnapshot(
    /** The plan on screen. */
    val date: LocalDate,
    /** The wall clock. */
    val time: LocalTime,
    val plannedBlocks: Int,
    /** Adherence of the blocks that have an outcome so far, 0 to 100, or null before there is one. */
    val adherence: Int?,
    val current: TimeBlock? = null,
    /** e.g. "1h 20m", as the Now card says it. */
    val left: String? = null,
    val next: TimeBlock? = null,
    /** When [next] starts, as the user's clock reads it. */
    val nextClock: String? = null,
)

/** What the buddy looks like and says on Today. */
data class BuddyOutlook(val mood: BuddyMood, val line: String)

/**
 * The buddy on Today: a face that follows how the day is going, and a line about what is happening
 * now. The face is decided by adherence so far — blocks with no outcome yet count for nothing
 * either way, so a morning with nothing scored is a happy one, not a failing one.
 */
class TodayBuddy(private val voice: BuddyVoice) {

    fun outlook(snapshot: TodaySnapshot, settings: BuddySettings): BuddyOutlook {
        val mood = moodFor(snapshot, settings)
        val bubble = when {
            snapshot.current?.category == Category.Sleep -> Bubble.SleepBlock
            snapshot.current == null && snapshot.time in settings.quietHours -> Bubble.Night
            snapshot.plannedBlocks == 0 -> Bubble.EmptyDay
            snapshot.current != null -> when (mood) {
                BuddyMood.Proud, BuddyMood.Happy -> Bubble.RunningGood
                BuddyMood.Encouraging -> Bubble.RunningSteady
                else -> Bubble.RunningBehind
            }
            snapshot.next != null -> Bubble.Free
            else -> when (mood) {
                BuddyMood.Proud, BuddyMood.Happy -> Bubble.DoneGood
                BuddyMood.Encouraging -> Bubble.DoneSteady
                else -> Bubble.DoneBehind
            }
        }
        // Stable for as long as the same block is on: the line changes when the moment does, not
        // every minute the screen redraws.
        val rotation = stableHash("$bubble:${snapshot.date}:${snapshot.current?.id?.value ?: snapshot.next?.id?.value.orEmpty()}")
        val slots = Slots(
            title = snapshot.current?.title,
            emoji = snapshot.current?.category?.emoji,
            left = snapshot.left,
            next = snapshot.next?.title,
            nextTime = snapshot.nextClock,
        )
        return BuddyOutlook(mood, voice.bubble(bubble, settings.name, rotation, slots))
    }

    internal fun moodFor(snapshot: TodaySnapshot, settings: BuddySettings): BuddyMood {
        val adherence = snapshot.adherence
        return when {
            snapshot.current?.category == Category.Sleep -> BuddyMood.Sleepy
            snapshot.current == null && snapshot.time in settings.quietHours -> BuddyMood.Sleepy
            snapshot.plannedBlocks == 0 -> BuddyMood.Encouraging
            adherence == null -> BuddyMood.Happy
            adherence >= 85 -> BuddyMood.Proud
            adherence >= 60 -> BuddyMood.Happy
            adherence >= 35 -> BuddyMood.Encouraging
            adherence >= 15 -> BuddyMood.Worried
            else -> BuddyMood.Disappointed
        }
    }
}
