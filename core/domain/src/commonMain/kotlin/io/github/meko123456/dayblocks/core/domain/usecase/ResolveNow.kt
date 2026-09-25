package io.github.meko123456.dayblocks.core.domain.usecase

import io.github.meko123456.dayblocks.core.domain.model.TimeBlock
import io.github.meko123456.dayblocks.core.domain.time.endInstant
import io.github.meko123456.dayblocks.core.domain.time.startInstant
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

/** What is happening right now, and what comes next — the Today screen's "Now" card. */
data class NowState(
    val current: TimeBlock?,
    /** Real time remaining, so a block spanning a DST change counts down the true length. */
    val timeLeft: Duration?,
    val next: TimeBlock?,
    val untilNext: Duration?,
)

/**
 * Finds the current and next block on real instants.
 *
 * Callers pass blocks from yesterday's and today's plans together. At 00:30 the current block is
 * very often yesterday's — "00:00 Sleep", or a 23:00–01:00 film — and a lookup confined to
 * today's plan would report nothing happening while the user is in the middle of a planned block.
 *
 * Instants rather than wall-clock minutes because this is the one question that is about real
 * time: on a spring-forward night a 01:00–03:00 block lasts one hour, and its countdown must say so.
 */
class ResolveNow {
    operator fun invoke(blocks: List<TimeBlock>, now: Instant, zone: TimeZone): NowState {
        val timed = blocks.map { Triple(it, it.startInstant(zone), it.endInstant(zone)) }
        // If two blocks overlap, the one that started later is the one the user moved on to.
        val current = timed
            .filter { (_, start, end) -> now >= start && now < end }
            .maxByOrNull { (_, start, _) -> start }
        val next = timed
            .filter { (_, start, _) -> start > now }
            .minByOrNull { (_, start, _) -> start }
        return NowState(
            current = current?.first,
            timeLeft = current?.let { (_, _, end) -> end - now },
            next = next?.first,
            untilNext = next?.let { (_, start, _) -> start - now },
        )
    }
}
