package io.github.meko123456.dayblocks.core.domain.usecase

import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.TimeBlock

/**
 * The unplanned gaps in a day, shown on the timeline as tappable "Free time" slots.
 *
 * Only within [window] — the timeline hours the user chose — because a gap between 03:00 and
 * 07:00 is sleep, not an invitation. Gaps shorter than [minimumMinutes] are dropped: the editor
 * works in 15-minute steps, so a five-minute sliver between two blocks is not something anyone
 * can fill.
 *
 * Overlapping blocks are merged first, so a gap is only reported where nothing is planned.
 */
class FindFreeTime(private val minimumMinutes: Int = 15) {
    operator fun invoke(blocks: List<TimeBlock>, window: DaySpan): List<DaySpan> {
        val busy = blocks
            .map { it.span }
            .filter { it.overlaps(window) }
            .sortedBy { it.startMinutes }

        val gaps = mutableListOf<DaySpan>()
        var cursor = window.startMinutes
        for (span in busy) {
            if (span.startMinutes > cursor) addGap(gaps, cursor, span.startMinutes)
            cursor = maxOf(cursor, span.endMinutes)
            if (cursor >= window.endMinutes) break
        }
        if (cursor < window.endMinutes) addGap(gaps, cursor, window.endMinutes)
        return gaps
    }

    private fun addGap(into: MutableList<DaySpan>, start: Int, end: Int) {
        if (end - start >= minimumMinutes) into += DaySpan(start, end)
    }
}
