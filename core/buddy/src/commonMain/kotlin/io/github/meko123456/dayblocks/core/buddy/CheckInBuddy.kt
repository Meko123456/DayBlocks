package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import kotlinx.datetime.LocalDate

/**
 * The buddy at the end-of-day check-in: how it takes the day's score. Warm at every level — a
 * low score gets comfort, never a telling-off — and waiting patiently while blocks are unrated.
 */
class CheckInBuddy(private val voice: BuddyVoice) {

    /**
     * @param score the day's adherence so far, or null before any block has an outcome
     * @param unrated blocks with neither an outcome nor an answer to suggest one
     */
    fun reaction(date: LocalDate, plannedBlocks: Int, score: Int?, unrated: Int, settings: BuddySettings): BuddyOutlook {
        val (bubble, mood) = when {
            plannedBlocks == 0 -> Bubble.ReviewEmpty to BuddyMood.Encouraging
            score == null || unrated > 0 -> Bubble.ReviewPending to BuddyMood.Encouraging
            score >= 85 -> Bubble.ReviewGreat to BuddyMood.Proud
            score >= 60 -> Bubble.ReviewGood to BuddyMood.Happy
            score >= 35 -> Bubble.ReviewOkay to BuddyMood.Encouraging
            score >= 15 -> Bubble.ReviewTough to BuddyMood.Worried
            else -> Bubble.ReviewTough to BuddyMood.Disappointed
        }
        // Stable for the day: rating one more block should not make the buddy change its mind
        // about how to say the same thing.
        val rotation = stableHash("$bubble:$date")
        return BuddyOutlook(mood, voice.bubble(bubble, settings.name, rotation, Slots(score = score)))
    }
}
