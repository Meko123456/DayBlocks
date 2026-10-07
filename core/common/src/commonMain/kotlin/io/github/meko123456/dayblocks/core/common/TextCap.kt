package io.github.meko123456.dayblocks.core.common

/**
 * At most [max] chars of this text, never ending in half of a surrogate pair.
 *
 * Most emoji are two chars (a surrogate pair), so a plain `take(max)` whose cut falls between
 * the two keeps the first half alone. That half shows as a broken character and isn't valid
 * Unicode. Here the cut moves back one char instead, so an emoji typed at the limit is left out
 * whole rather than kept in half.
 */
fun String.capped(max: Int): String {
    val cut = take(max)
    return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
