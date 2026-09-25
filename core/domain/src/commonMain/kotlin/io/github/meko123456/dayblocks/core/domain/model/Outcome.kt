package io.github.meko123456.dayblocks.core.domain.model

/**
 * How a block actually went, as the end-of-day check-in records it.
 */
enum class BlockOutcome {
    Done, Partly, Skipped;

    /**
     * How much of the planned time this outcome counts as followed.
     *
     * Partly is a half rather than anything cleverer: the check-in is one tap, and a score that
     * pretends to know whether "partly" meant twenty minutes or two hours would be precision the
     * input never had.
     */
    val weight: Double
        get() = when (this) {
            Done -> 1.0
            Partly -> 0.5
            Skipped -> 0.0
        }
}

/**
 * An answer given from a notification during the day, before any check-in. The end-of-day
 * check-in is prefilled from these, which is why they are stored rather than only acted on.
 */
enum class CheckInAnswer {
    OnIt, GotDistracted, SkipBlock;

    /** The check-in prefill: on it reads as done, distracted as partly, skipping as skipped. */
    val suggestedOutcome: BlockOutcome
        get() = when (this) {
            OnIt -> BlockOutcome.Done
            GotDistracted -> BlockOutcome.Partly
            SkipBlock -> BlockOutcome.Skipped
        }
}
