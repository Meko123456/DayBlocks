package io.github.meko123456.dayblocks.core.buddy

import org.koin.dsl.module

/** The planner speaks with [PlainVoice] until the buddy engine gives it a voice of its own. */
val buddyModule = module {
    factory<BuddyVoice> { PlainVoice() }
    factory { NotificationPlanner(get()) }
}
