package io.github.meko123456.dayblocks.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers as a dependency rather than a global, so a test can run everything on its own
 * scheduler and `runTest` can control virtual time instead of waiting for real IO.
 */
interface AppDispatchers {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
}

object DefaultAppDispatchers : AppDispatchers {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = platformIoDispatcher
    override val default: CoroutineDispatcher get() = Dispatchers.Default
}

/**
 * `Dispatchers.IO` exists on both platforms but not in common code: on the JVM it is a member, on
 * Native it is an extension in `kotlinx.coroutines` that commonMain cannot see. So each platform
 * names its own, rather than common code quietly falling back to `Default` for disk work.
 */
internal expect val platformIoDispatcher: CoroutineDispatcher
