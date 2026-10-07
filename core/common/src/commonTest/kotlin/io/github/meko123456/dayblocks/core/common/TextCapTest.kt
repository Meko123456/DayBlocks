package io.github.meko123456.dayblocks.core.common

import kotlin.test.Test
import kotlin.test.assertEquals

class TextCapTest {

    private val fire = "🔥" // 🔥, two chars

    @Test
    fun textWithinTheCapIsUntouched() {
        assertEquals("Gym", "Gym".capped(40))
        assertEquals("Gym $fire", "Gym $fire".capped(6))
        assertEquals("", "".capped(40))
    }

    @Test
    fun aLongTextIsCutAtTheCap() {
        assertEquals("x".repeat(40), "x".repeat(200).capped(40))
    }

    @Test
    fun anEmojiAcrossTheCapIsLeftOutWholeNotKeptInHalf() {
        val typed = "x".repeat(39) + fire
        assertEquals("x".repeat(39), typed.capped(40))
        assertEquals("x".repeat(38) + fire, ("x".repeat(38) + fire + fire).capped(40), "one that fits stays")
    }
}
