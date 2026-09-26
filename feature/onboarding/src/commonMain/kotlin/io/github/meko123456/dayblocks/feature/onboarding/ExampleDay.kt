package io.github.meko123456.dayblocks.feature.onboarding

import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.DaySpan
import io.github.meko123456.dayblocks.core.domain.model.Template
import io.github.meko123456.dayblocks.core.domain.model.TemplateBlock
import io.github.meko123456.dayblocks.core.domain.model.TemplateId

/**
 * A plausible weekday to start from: something to edit rather than an empty page to fill. Saved
 * as the user's first template, so it is also one tap to reuse.
 */
internal val ExampleDay = Template(
    id = TemplateId("example-day"),
    name = "Example day",
    blocks = listOf(
        TemplateBlock("Wake up and stretch", Category.Exercise, DaySpan.of(7, 30, 8, 0)),
        TemplateBlock("Deep work", Category.Work, DaySpan.of(9, 0, 12, 0)),
        TemplateBlock("Lunch", Category.Cooking, DaySpan.of(12, 0, 13, 0)),
        TemplateBlock("Reading", Category.Reading, DaySpan.of(13, 0, 14, 0)),
        TemplateBlock("Work", Category.Work, DaySpan.of(14, 0, 17, 0)),
        TemplateBlock("Gym", Category.Exercise, DaySpan.of(18, 0, 19, 0)),
        TemplateBlock("Dinner", Category.Cooking, DaySpan.of(19, 30, 20, 30)),
        TemplateBlock("Wind down", Category.Rest, DaySpan.of(21, 0, 22, 30)),
        TemplateBlock("Sleep", Category.Sleep, DaySpan(23 * 60, 31 * 60)),
    ),
)
