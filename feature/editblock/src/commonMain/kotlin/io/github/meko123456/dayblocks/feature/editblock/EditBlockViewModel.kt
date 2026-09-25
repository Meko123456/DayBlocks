package io.github.meko123456.dayblocks.feature.editblock

import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.designsystem.mvi.MviViewModel
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.MINUTES_PER_DAY
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.DetectOverlaps
import io.github.meko123456.dayblocks.core.domain.usecase.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Add or edit one block.
 *
 * Times move in quarter hours, the granularity a day is planned in. Overlaps are checked against
 * the neighbouring days too, because Monday's "00:00 Sleep" and a Tuesday 00:30 block collide
 * while belonging to different plans.
 */
class EditBlockViewModel(
    private val args: EditBlockArgs,
    private val blocks: BlockRepository,
    private val detectOverlaps: DetectOverlaps,
    private val ids: IdGenerator,
    private val clock: TimeProvider,
    private val planningDay: PlanningDayRule,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : MviViewModel<EditBlockState, EditBlockIntent, EditBlockEffect>(EditBlockState(date = args.date), scope) {

    private var recentTitles: List<String> = emptyList()
    private var neighbours: List<TimeBlock> = emptyList()

    init {
        // One coroutine, in a fixed order. An earlier version loaded the block and collected the
        // neighbouring days in two coroutines, and the first "loaded" state was published before
        // the neighbours arrived — the editor claimed "no overlaps", then flickered a warning in.
        // Initialising from the first neighbour emission makes that impossible, and the block
        // being edited is on this day anyway, so no separate lookup is needed.
        launchInScope {
            recentTitles = blocks.recentTitles(limit = RECENT_TITLES)
            combine(
                blocks.observeDay(args.date.minus(1, DateTimeUnit.DAY)),
                blocks.observeDay(args.date),
                blocks.observeDay(args.date.plus(1, DateTimeUnit.DAY)),
            ) { before, day, after -> before + day + after }
                .collect { all ->
                    neighbours = all
                    if (state.value.loading) initialise(all) else reduce { withOverlaps() }
                }
        }
    }

    private suspend fun initialise(all: List<TimeBlock>) {
        val existing = args.blockId?.let { id -> all.firstOrNull { it.id == id } }
        if (args.blockId != null && existing == null) {
            // Deleted from under us — by a template applied elsewhere, say. Nothing to edit.
            emit(EditBlockEffect.Close)
            return
        }
        reduce {
            val loaded = if (existing != null) {
                copy(
                    isNew = false,
                    title = existing.title,
                    category = existing.category,
                    span = existing.span,
                    note = existing.note.orEmpty(),
                )
            } else {
                copy(span = defaultSpan())
            }
            loaded.copy(loading = false).withSuggestions().withOverlaps()
        }
    }

    override suspend fun handle(intent: EditBlockIntent) {
        when (intent) {
            is EditBlockIntent.TitleChanged -> reduce { copy(title = intent.title.take(MAX_TITLE)).withSuggestions() }
            is EditBlockIntent.SuggestionPicked -> reduce { copy(title = intent.title, suggestions = emptyList()) }
            is EditBlockIntent.CategoryPicked -> reduce { copy(category = intent.category) }
            is EditBlockIntent.StartStepped -> reduce { copy(span = span.withStartStepped(intent.steps)).withOverlaps() }
            is EditBlockIntent.EndStepped -> reduce { copy(span = span.withEndStepped(intent.steps)).withOverlaps() }
            is EditBlockIntent.NoteChanged -> reduce { copy(note = intent.note) }
            EditBlockIntent.SaveTapped -> save()
            EditBlockIntent.DeleteTapped -> delete()
            EditBlockIntent.CancelTapped -> emit(EditBlockEffect.Close)
        }
    }

    private suspend fun save() {
        val s = state.value
        if (!s.canSave) return
        blocks.upsert(
            TimeBlock(
                id = args.blockId ?: BlockId(ids.next()),
                date = s.date,
                title = s.title.trim(),
                category = s.category,
                span = s.span,
                note = s.note.trim().ifBlank { null },
            ),
        )
        emit(EditBlockEffect.Close)
    }

    private suspend fun delete() {
        val id = args.blockId ?: return
        blocks.delete(id)
        emit(EditBlockEffect.Close)
    }

    /**
     * A new block's starting span. Filling a free-time gap starts at the gap and lasts an hour or
     * the gap, whichever is shorter. Otherwise, planning *today* starts at the next quarter hour —
     * nobody plans a block for 09:00 at 15:40 — and any other day starts at 09:00.
     */
    private fun defaultSpan(): DaySpan {
        args.prefill?.let { gap ->
            return DaySpan(gap.startMinutes, minOf(gap.endMinutes, gap.startMinutes + DEFAULT_MINUTES))
        }
        val nowLocal = clock.now().toLocalDateTime(clock.zone())
        val start = if (planningDay.planDateAt(nowLocal) == args.date) {
            val minute = args.date.daysUntil(nowLocal.date) * MINUTES_PER_DAY + nowLocal.hour * 60 + nowLocal.minute
            ((minute + QUARTER - 1) / QUARTER * QUARTER).coerceAtMost(DaySpan.MAX_START - DEFAULT_MINUTES)
        } else {
            9 * 60
        }
        return DaySpan(start, start + DEFAULT_MINUTES)
    }

    private fun EditBlockState.withSuggestions(): EditBlockState {
        val typed = title.trim()
        val matches = recentTitles
            .filter { typed.isEmpty() || it.contains(typed, ignoreCase = true) }
            .filterNot { it.equals(typed, ignoreCase = true) }
            .take(MAX_SUGGESTIONS)
        return copy(suggestions = matches)
    }

    private fun EditBlockState.withOverlaps(): EditBlockState =
        copy(overlaps = detectOverlaps(date, span, neighbours, editing = args.blockId))

    companion object {
        const val QUARTER: Int = 15
        const val DEFAULT_MINUTES: Int = 60
        const val MAX_TITLE: Int = 80
        const val MAX_SUGGESTIONS: Int = 5
        const val RECENT_TITLES: Int = 20
    }
}

/**
 * Lands on a quarter-hour boundary even from an odd minute: 09:07 stepped later is 09:15, stepped
 * earlier is 09:00 — never 09:22 or 08:52.
 */
internal fun stepToQuarter(value: Int, steps: Int): Int {
    if (steps == 0) return value
    val q = EditBlockViewModel.QUARTER
    val offset = value.mod(q)
    val base = when {
        offset == 0 -> value
        steps > 0 -> value - offset
        else -> value - offset + q
    }
    return base + q * steps
}

/** Moves the start, pushing the end along if the start would reach it. Never shorter than 15 minutes. */
internal fun DaySpan.withStartStepped(steps: Int): DaySpan {
    val q = EditBlockViewModel.QUARTER
    val start = stepToQuarter(startMinutes, steps).coerceIn(0, DaySpan.MAX_START - q)
    val end = endMinutes.coerceIn(start + q, start + MINUTES_PER_DAY)
    return DaySpan(start, end)
}

/** Moves the end, never before the start plus a quarter hour and never beyond a day. */
internal fun DaySpan.withEndStepped(steps: Int): DaySpan {
    val q = EditBlockViewModel.QUARTER
    val end = stepToQuarter(endMinutes, steps).coerceIn(startMinutes + q, startMinutes + MINUTES_PER_DAY)
    return DaySpan(startMinutes, end)
}
