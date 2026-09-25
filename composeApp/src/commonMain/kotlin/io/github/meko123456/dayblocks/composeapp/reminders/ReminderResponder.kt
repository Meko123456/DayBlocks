package io.github.meko123456.dayblocks.composeapp.reminders

import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository

/**
 * What a notification's button does, on both platforms: record the answer, which the end-of-day
 * check-in is prefilled from, then reschedule. The follow-up after "Got distracted" is not sent
 * from here — the planner derives it from the answer, so it survives every later reschedule too.
 */
class ReminderResponder(
    private val outcomes: OutcomeRepository,
    private val clock: TimeProvider,
    private val rescheduler: ReminderRescheduler,
) {
    suspend fun answer(block: BlockId, answer: CheckInAnswer) {
        outcomes.recordAnswer(block, answer, clock.now())
        // Now rather than when the plan observer settles: on Android this runs inside a broadcast
        // receiver, and the process may be gone as soon as the receiver finishes.
        rescheduler.rescheduleNow()
    }
}
