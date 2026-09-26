package io.github.meko123456.dayblocks.core.buddy

import io.github.meko123456.dayblocks.core.common.formatClock
import io.github.meko123456.dayblocks.core.common.formatDuration
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind
import io.github.meko123456.dayblocks.core.domain.model.QuietHours
import io.github.meko123456.dayblocks.core.domain.model.ScheduledNotification
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.time.endInstant
import io.github.meko123456.dayblocks.core.domain.time.startInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
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
import kotlinx.datetime.plus
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
    val settings: BuddySettings = BuddySettings(),
    /** Days in a row, before today, that followed their plan. */
    val streak: Int = 0,
    /** When the app was last opened. Comebacks are counted from it. */
    val lastOpened: Instant? = null,
    val rule: PlanningDayRule = PlanningDayRule(),
)

/**
 * The buddy's schedule: from the plan to the notifications of the next [WINDOW].
 *
 * It speaks at each block's start; checks in partway through the long ones, with the three
 * answers as buttons; follows up after "Got distracted"; reminds in the evening, and again in the
 * morning, about a day with nothing planned; protects a streak; says it misses the user after a
 * day away; and asks for the end-of-day review. How often depends on the tone.
 *
 * Then two rules keep it motivating rather than annoying. **Quiet hours** keep the buddy's own
 * voice down: a check-in or a follow-up inside them is dropped, a reminder that can wait is moved
 * to where they end. A block the user planned inside them still announces its start — planning a
 * 07:00 run is asking to hear about it at 07:00. **The daily cap**: a day's budget is spent in
 * priority order, block starts first, comebacks last. The budget covers the whole planning day, past moments included,
 * so a rebuild at 15:00 cannot hand out again what 09:00 already spent.
 *
 * Pure. The same plan at the same moment gives the same list, with the same ids and the same
 * words, which is what lets the caller rebuild the schedule on every change instead of patching
 * it. Everything is computed on real instants, so a block across midnight or a DST change fires
 * when the wall clock says it should, and a block's length is how long it actually lasts.
 */
class NotificationPlanner(private val voice: BuddyVoice) {

    fun plan(input: PlanInput): List<ScheduledNotification> {
        val zone = input.zone
        val tone = TonePolicy.of(input.settings.tone)
        val today = input.rule.planDateAt(input.now.toLocalDateTime(zone))
        fun planDateOf(at: Instant) = input.rule.planDateAt(at.toLocalDateTime(zone))

        val candidates = buildList {
            for (day in input.days) {
                for (block in day.blocks) addAll(forBlock(block, day.records[block.id], tone, input))
                if (day.date >= today) review(day, zone)?.let(::add)
            }
            addAll(planningReminders(input, today, tone))
            streak(input, today)?.let(::add)
            addAll(comebacks(input))
        }

        val heard = candidates.mapNotNull { it.throughQuietHours(input.settings.quietHours, zone) }
        val kept = heard.groupBy { planDateOf(it.at) }.values.flatMap { day ->
            day.sortedWith(compareBy<Candidate> { it.kind.priority }.thenBy { it.at }).take(input.settings.dailyCap)
        }
        // Words last. Each situation's occurrences are numbered through the whole day, past ones
        // included, so a notification keeps its words across rebuilds and neighbours never match.
        val spoken = kept.groupBy { it.situation to planDateOf(it.at) }.flatMap { (key, occurrences) ->
            val base = stableHash("${key.first}:${key.second}")
            occurrences.sortedBy { it.at }.mapIndexed { i, candidate -> candidate.spoken(voice, input.settings, base + i) }
        }

        val windowEnd = input.now + WINDOW
        return spoken
            .filter { it.at > input.now && it.at < windowEnd }
            .sortedWith(compareBy<ScheduledNotification> { it.at }.thenBy { it.kind.ordinal })
            .take(MAX_PENDING)
    }

    private fun forBlock(block: TimeBlock, record: BlockRecord?, tone: TonePolicy, input: PlanInput): List<Candidate> {
        val start = block.startInstant(input.zone)
        val end = block.endInstant(input.zone)
        val length = end - start
        val id = block.id.value
        val out = mutableListOf<Candidate>()

        out += Candidate(
            "start:$id", start, NotificationKind.BlockStart, Situation.BlockStart,
            Slots(block.title, block.category.emoji, time = start.clockReading(input.zone, input.is24Hour), length = spokenLength(length.inWholeMinutes.toInt())),
            block.id,
            quiet = Quiet.Speak,
        )
        // Nobody asks "still sleeping?", and a short block is over before the question would help.
        // An answer already given needs no second ask.
        if (record?.answer == null && block.category != Category.Sleep && length >= tone.minCheckInLength) {
            val count = tone.checkInsFor(length)
            for (k in 1..count) {
                val at = (start + length * k / (count + 1)).floorToMinute()
                out += Candidate(
                    if (k == 1) "checkin:$id" else "checkin$k:$id", at, NotificationKind.MidBlockCheckIn, Situation.CheckIn,
                    Slots(block.title, block.category.emoji, left = formatDuration((end - at).inWholeMinutes.toInt())),
                    block.id, CheckInAnswer.entries,
                )
            }
        }
        val answeredAt = record?.answeredAt
        if (record?.answer == CheckInAnswer.GotDistracted && answeredAt != null) {
            tone.nudgeDelays.forEachIndexed { i, delay ->
                val at = answeredAt + delay
                val left = end - at
                // With a few minutes left, "let's go back to it" is not encouragement, it is noise.
                if (left >= MIN_LEFT_FOR_NUDGE) {
                    out += Candidate(
                        if (i == 0) "nudge:$id" else "nudge${i + 1}:$id", at, NotificationKind.Nudge, Situation.BackOnTrack,
                        Slots(block.title, block.category.emoji, left = formatDuration(left.inWholeMinutes.toInt())),
                        block.id,
                    )
                }
            }
        }
        return out
    }

    /** Shortly after the day's last waking block; nothing once every block has its outcome. */
    private fun review(day: PlannedDay, zone: TimeZone): Candidate? {
        val waking = day.blocks.filter { it.category != Category.Sleep }
        if (waking.isEmpty()) return null
        if (day.blocks.all { day.records[it.id]?.outcome != null }) return null
        val at = waking.maxOf { it.endInstant(zone) } + REVIEW_DELAY
        return Candidate("review:${day.date}", at, NotificationKind.EndOfDay, Situation.ReviewDay, Slots())
    }

    /**
     * For a day with nothing planned that will not fill itself: the evening before, and — unless
     * the tone is gentle — again that morning. The evening one is about "tomorrow", so it cannot
     * move past midnight; the morning one can wait for quiet hours to end.
     */
    private fun planningReminders(input: PlanInput, today: LocalDate, tone: TonePolicy): List<Candidate> = buildList {
        for (target in input.days) {
            if (target.date < today || target.blocks.isNotEmpty() || target.date.dayOfWeek in input.templatedWeekdays) continue
            val evening = LocalDateTime(target.date.minus(1, DateTimeUnit.DAY), EVENING_REMINDER).toInstant(input.zone)
            add(Candidate("plan:${target.date}:evening", evening, NotificationKind.PlanningReminder, Situation.PlanTomorrow, Slots()))
            if (tone.morningPlanning) {
                val morning = LocalDateTime(target.date, MORNING_REMINDER).toInstant(input.zone)
                add(Candidate("plan:${target.date}:morning", morning, NotificationKind.PlanningReminder, Situation.PlanToday, Slots(), quiet = Quiet.Shift))
            }
        }
    }

    /** A morning word for a streak worth protecting, on a day there is a plan to follow. */
    private fun streak(input: PlanInput, today: LocalDate): Candidate? {
        if (input.streak < MIN_STREAK) return null
        if (input.days.firstOrNull { it.date == today }?.blocks.isNullOrEmpty()) return null
        val at = LocalDateTime(today, STREAK_REMINDER).toInstant(input.zone)
        return Candidate("streak:$today", at, NotificationKind.Streak, Situation.Streak, Slots(streak = input.streak), quiet = Quiet.Shift)
    }

    /**
     * Counted from the last time the app was opened, so opening it moves them all along and only
     * a real absence lets one through: after a day, after three, after a week.
     */
    private fun comebacks(input: PlanInput): List<Candidate> {
        val opened = input.lastOpened ?: return emptyList()
        return COMEBACK_AFTER.map { after ->
            Candidate("comeback:${after.inWholeDays}d", opened + after, NotificationKind.Comeback, Situation.Comeback, Slots(), quiet = Quiet.Shift)
        }
    }

    companion object {
        /** How far ahead is scheduled. iOS's 64-pending limit is why it is a window at all. */
        val WINDOW = 36.hours

        /** iOS's limit on pending notifications, applied on both platforms so they agree. */
        const val MAX_PENDING: Int = 64

        /** A streak is worth protecting from two days. */
        const val MIN_STREAK: Int = 2

        val MIN_LEFT_FOR_NUDGE = 5.minutes
        val REVIEW_DELAY = 10.minutes
        val EVENING_REMINDER = LocalTime(20, 0)
        val MORNING_REMINDER = LocalTime(8, 30)
        val STREAK_REMINDER = LocalTime(9, 0)
        val COMEBACK_AFTER = listOf(1.days, 3.days, 7.days)
    }
}

/**
 * How often each tone speaks. Gentle asks only about long blocks and follows up once, later;
 * Pushy asks about shorter ones, twice about the longest, and follows up twice.
 */
internal class TonePolicy private constructor(
    val minCheckInLength: Duration,
    private val twoCheckInsFrom: Duration?,
    val nudgeDelays: List<Duration>,
    val morningPlanning: Boolean,
) {
    fun checkInsFor(length: Duration): Int = if (twoCheckInsFrom != null && length >= twoCheckInsFrom) 2 else 1

    companion object {
        fun of(tone: BuddyTone): TonePolicy = when (tone) {
            BuddyTone.Gentle -> TonePolicy(90.minutes, null, listOf(15.minutes), morningPlanning = false)
            BuddyTone.Normal -> TonePolicy(60.minutes, null, listOf(10.minutes), morningPlanning = true)
            BuddyTone.Pushy -> TonePolicy(45.minutes, 120.minutes, listOf(10.minutes, 25.minutes), morningPlanning = true)
        }
    }
}

/** A notification before its words: what the planner decided, not yet what the buddy says. */
private data class Candidate(
    val id: String,
    val at: Instant,
    val kind: NotificationKind,
    val situation: Situation,
    val slots: Slots,
    val blockId: BlockId? = null,
    val actions: List<CheckInAnswer> = emptyList(),
    val quiet: Quiet = Quiet.Drop,
) {
    fun throughQuietHours(hours: QuietHours, zone: TimeZone): Candidate? {
        val local = at.toLocalDateTime(zone)
        if (quiet == Quiet.Speak || local.time !in hours) return this
        if (quiet == Quiet.Drop) return null
        val endDate = if (local.time < hours.end) local.date else local.date.plus(1, DateTimeUnit.DAY)
        return copy(at = LocalDateTime(endDate, hours.end).toInstant(zone))
    }

    fun spoken(voice: BuddyVoice, settings: BuddySettings, rotation: Int): ScheduledNotification {
        val line = voice.line(situation, settings.tone, settings.name, rotation, slots)
        return ScheduledNotification(id = id, at = at, kind = kind, title = line.title, body = line.body, blockId = blockId, actions = actions)
    }
}

/** What a notification does inside quiet hours. */
private enum class Quiet {
    /** Still said: the user planned this moment themselves. */
    Speak,

    /** Only worth saying at its moment, so let go. */
    Drop,

    /** Can wait, so said when quiet hours end. */
    Shift,
}

/** What survives the daily cap first. */
private val NotificationKind.priority: Int
    get() = when (this) {
        NotificationKind.BlockStart -> 0
        NotificationKind.MidBlockCheckIn -> 1
        NotificationKind.Nudge -> 2
        NotificationKind.EndOfDay -> 3
        NotificationKind.PlanningReminder -> 4
        NotificationKind.Streak -> 5
        NotificationKind.Comeback -> 6
    }

/** The wall-clock reading of this moment, as the user's clock shows it. */
private fun Instant.clockReading(zone: TimeZone, is24Hour: Boolean): String {
    val local = toLocalDateTime(zone)
    return formatClock(local.hour * 60 + local.minute, is24Hour)
}

private fun Instant.floorToMinute(): Instant = Instant.fromEpochSeconds(epochSeconds - epochSeconds.mod(60L))
