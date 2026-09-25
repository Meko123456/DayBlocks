package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.BlockOutcome
import io.github.meko123456.dayblocks.core.domain.model.BlockRecord
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BlockRecordTest {

    private val id = BlockId("work")

    @Test
    fun theCheckInOutcomeWinsOverTheNotificationAnswer() {
        // Tapped "On it" at 10:30, then admitted at the check-in it only partly happened.
        val record = BlockRecord(id, answer = CheckInAnswer.OnIt, outcome = BlockOutcome.Partly)
        assertEquals(BlockOutcome.Partly, record.effectiveOutcome)
    }

    @Test
    fun untilTheCheckInTheAnswerStandsIn() {
        assertEquals(BlockOutcome.Done, BlockRecord(id, answer = CheckInAnswer.OnIt).effectiveOutcome)
        assertEquals(BlockOutcome.Partly, BlockRecord(id, answer = CheckInAnswer.GotDistracted).effectiveOutcome)
        assertEquals(BlockOutcome.Skipped, BlockRecord(id, answer = CheckInAnswer.SkipBlock).effectiveOutcome)
    }

    @Test
    fun withNeitherThereIsNoOutcome() {
        assertNull(BlockRecord(id).effectiveOutcome)
    }

    @Test
    fun outcomeWeightsAreWholeHalfAndNone() {
        assertEquals(1.0, BlockOutcome.Done.weight)
        assertEquals(0.5, BlockOutcome.Partly.weight)
        assertEquals(0.0, BlockOutcome.Skipped.weight)
    }
}
