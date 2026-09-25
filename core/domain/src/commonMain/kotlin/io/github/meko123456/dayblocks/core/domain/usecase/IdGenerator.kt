package io.github.meko123456.dayblocks.core.domain.usecase

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Where new ids come from. An interface so a test can assert on exact ids instead of on "some
 * string", and so generating a day from a template is deterministic under test.
 */
fun interface IdGenerator {
    fun next(): String
}

@OptIn(ExperimentalUuidApi::class)
object RandomIdGenerator : IdGenerator {
    override fun next(): String = Uuid.random().toString()
}
