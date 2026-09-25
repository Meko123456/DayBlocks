package io.github.meko123456.dayblocks.core.common

import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Now, and then every time the wall clock turns a minute.
 *
 * Aligned to the minute boundary rather than "every 60 seconds from whenever we started", so a
 * block ending at 12:00 changes the screen at 12:00:00 and not at 12:00:37. Minute precision is
 * the whole contract: the Now card counts down in minutes, and ticking every second would
 * recompose the entire timeline sixty times as often to change nothing a person can read.
 */
fun TimeProvider.minuteTicks(): Flow<Instant> = flow {
    while (true) {
        val now = now()
        emit(now)
        val intoMinute = now.toEpochMilliseconds().mod(MILLIS_PER_MINUTE)
        delay(MILLIS_PER_MINUTE - intoMinute)
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
