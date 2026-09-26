package io.github.meko123456.dayblocks.core.buddy

import org.koin.dsl.module

/** The buddy's voice and its schedule. Both stateless. */
val buddyModule = module {
    factory { BuddyVoice() }
    factory { NotificationPlanner(get()) }
}
