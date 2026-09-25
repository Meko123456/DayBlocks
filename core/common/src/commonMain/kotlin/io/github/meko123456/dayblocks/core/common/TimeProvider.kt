package io.github.meko123456.dayblocks.core.common

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The only way anything in DayBlocks learns what time it is.
 *
 * Injected rather than called statically because almost every rule in this app is a question about
 * now — which block is current, whether a check-in is inside quiet hours, whether a streak
 * survived midnight — and a test that cannot move the clock can only assert the trivial half of
 * any of them. [FixedTimeProvider] is the test seam.
 *
 * The zone is part of the interface, not read from the platform at the call site, because a plan
 * is a wall-clock thing: "09:00 work" means 09:00 where the user is, and travelling across a time
 * zone must not silently reschedule their day.
 */
interface TimeProvider {
    fun now(): Instant
    fun zone(): TimeZone
    fun nowLocal(): LocalDateTime = now().toLocalDateTime(zone())
}

/** The real clock and the device's current zone. */
class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
    override fun zone(): TimeZone = TimeZone.currentSystemDefault()
}

/** A clock that does not move unless a test moves it. */
class FixedTimeProvider(
    var instant: Instant,
    var timeZone: TimeZone = TimeZone.UTC,
) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): TimeZone = timeZone
}
