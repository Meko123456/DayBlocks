package io.github.meko123456.dayblocks.core.domain

/**
 * What a block is for. A closed set rather than free text because the category drives the colour,
 * the buddy's wording and the stats — all of which need to mean the same thing every day.
 *
 * [Other] is the escape hatch, and the reason there is no "add your own category" yet: a custom
 * category would need its own colour, its own buddy phrasing and a migration, and none of that is
 * worth it before the app has been lived with.
 */
enum class Category {
    Work, Rest, Reading, Exercise, Cooking, Sleep, Personal, Other,
}
