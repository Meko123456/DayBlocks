package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.common.formatClock
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind
import io.github.meko123456.dayblocks.core.domain.model.ScheduledNotification
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.time.endInstant
import io.github.meko123456.dayblocks.core.domain.time.startInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** One planning day as the planner sees it: its blocks, and whatever has been recorded about them. */
data class PlannedDay(
    val date: LocalDate,
    val blocks: List<TimeBlock>,
    val records: Map<BlockId, BlockRecord> = emptyMap(),
)

/**
 * Everything the planner needs, gathered by the caller.
 *
 * [days] should run from the day before today's plan, whose last block can still be running after
 * midnight, to the day after the window ends, so "tomorrow's still empty" can be decided for every
 * evening in reach. A day that is absent is not guessed at.
 */
data class PlanInput(
    val now: Instant,
    val zone: TimeZone,
    val is24Hour: Boolean,
    val days: List<PlannedDay>,
    /** Weekdays with a template assigned. Such a day fills itself, so it is never "still empty". */
    val templatedWeekdays: Set<DayOfWeek> = emptySet(),
    val rule: PlanningDayRule = PlanningDayRule(),
)

/**
 * Turns the plan into the notifications of the next [WINDOW]: each block's start, a check-in
 * halfway through the long ones, the follow-up ten minutes after "Got distracted", an evening and
 * a morning reminder for a day with nothing planned, and the end-of-day review.
 *
 * Pure. The same plan at the same moment gives the same list with the same ids, which is what lets
 * the caller rebuild the whole schedule on every change instead of patching it. Everything is
 * computed on real instants, so a block across midnight or a DST change fires when the wall clock
 * says it should, and a block's length is how long it actually lasts.
 *
 * *When* is decided here and the words come from [voice]. Quiet hours, the daily cap and the tone
 * belong to the buddy engine, on top of this.
 */
class NotificationPlanner(private val voice: BuddyVoice) {

    fun plan(input: PlanInput): List<ScheduledNotification> {
        val now = input.now
        val zone = input.zone
        val windowEnd = now + WINDOW
        // Strictly after now: a notification for this very moment would be delivered late or never,
        // and the caller reschedules on every change anyway.
        fun inReach(at: Instant) = at > now && at < windowEnd

        val today = input.rule.planDateAt(now.toLocalDateTime(zone))
        val planned = mutableListOf<ScheduledNotification>()

        for (day in input.days) {
            for (block in day.blocks) {
                planned += forBlock(block, day.records[block.id], input).filter { inReach(it.at) }
            }
            if (day.date >= today) review(day, zone)?.takeIf { inReach(it.at) }?.let { planned += it }
        }

        for (target in input.days) {
            if (target.date < today || target.blocks.isNotEmpty() || target.date.dayOfWeek in input.templatedWeekdays) continue
            val evening = LocalDateTime(target.date.minus(1, DateTimeUnit.DAY), EVENING_REMINDER).toInstant(zone)
            if (inReach(evening)) planned += notification("plan:${target.date}:evening", evening, NotificationKind.PlanningReminder, voice.planTomorrow())
            val morning = LocalDateTime(target.date, MORNING_REMINDER).toInstant(zone)
            if (inReach(morning)) planned += notification("plan:${target.date}:morning", morning, NotificationKind.PlanningReminder, voice.planToday())
        }

        return planned
            .distinctBy { it.id }
            .sortedWith(compareBy<ScheduledNotification> { it.at }.thenBy { it.kind.ordinal })
            .take(MAX_PENDING)
    }

    private fun forBlock(block: TimeBlock, record: BlockRecord?, input: PlanInput): List<ScheduledNotification> {
        val start = block.startInstant(input.zone)
        val end = block.endInstant(input.zone)
        val length = end - start
        val id = block.id.value
        val out = mutableListOf<ScheduledNotification>()

        out += notification(
            "start:$id", start, NotificationKind.BlockStart,
            voice.blockStart(block, start.clockReading(input.zone, input.is24Hour), length.inWholeMinutes.toInt()),
            block.id,
        )
        // Nobody asks "still sleeping?", and a short block is over before the question would help.
        // An answer already given — from this check-in, before a reschedule — needs no second ask.
        if (record?.answer == null && block.category != Category.Sleep && length >= MIN_CHECK_IN_LENGTH) {
            val at = (start + length / 2).floorToMinute()
            out += notification(
                "checkin:$id", at, NotificationKind.MidBlockCheckIn,
                voice.checkIn(block, (end - at).inWholeMinutes.toInt()),
                block.id, CheckInAnswer.entries,
            )
        }
        val answeredAt = record?.answeredAt
        if (record?.answer == CheckInAnswer.GotDistracted && answeredAt != null) {
            val at = answeredAt + NUDGE_DELAY
            val left = end - at
            // With a few minutes left, "let's go back to it" is not encouragement, it is noise.
            if (left >= MIN_LEFT_FOR_NUDGE) {
                out += notification("nudge:$id", at, NotificationKind.Nudge, voice.backOnTrack(block, left.inWholeMinutes.toInt()), block.id)
            }
        }
        return out
    }

    /** Shortly after the day's last waking block; nothing once every block has its outcome. */
    private fun review(day: PlannedDay, zone: TimeZone): ScheduledNotification? {
        val waking = day.blocks.filter { it.category != Category.Sleep }
        if (waking.isEmpty()) return null
        if (day.blocks.all { day.records[it.id]?.outcome != null }) return null
        val at = waking.maxOf { it.endInstant(zone) } + REVIEW_DELAY
        return notification("review:${day.date}", at, NotificationKind.EndOfDay, voice.reviewDay())
    }

    private fun notification(
        id: String,
        at: Instant,
        kind: NotificationKind,
        line: Line,
        blockId: BlockId? = null,
        actions: List<CheckInAnswer> = emptyList(),
    ) = ScheduledNotification(id = id, at = at, kind = kind, title = line.title, body = line.body, blockId = blockId, actions = actions)

    companion object {
        /** How far ahead is scheduled. iOS's 64-pending limit is why it is a window at all. */
        val WINDOW = 36.hours

        /** iOS's limit on pending notifications, applied on both platforms so they agree. */
        const val MAX_PENDING: Int = 64

        /** Blocks shorter than this get no mid-block check-in. */
        val MIN_CHECK_IN_LENGTH = 60.minutes

        val NUDGE_DELAY = 10.minutes
        val MIN_LEFT_FOR_NUDGE = 5.minutes
        val REVIEW_DELAY = 10.minutes
        val EVENING_REMINDER = LocalTime(20, 0)
        val MORNING_REMINDER = LocalTime(8, 30)
    }
}

/** The wall-clock reading of this moment, as the user's clock shows it. */
private fun Instant.clockReading(zone: TimeZone, is24Hour: Boolean): String {
    val local = toLocalDateTime(zone)
    return formatClock(local.hour * 60 + local.minute, is24Hour)
}

private fun Instant.floorToMinute(): Instant = Instant.fromEpochSeconds(epochSeconds - epochSeconds.mod(60L))
