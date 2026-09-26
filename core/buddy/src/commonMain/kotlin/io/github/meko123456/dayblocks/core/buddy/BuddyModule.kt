package io.github.meko123456.dayblocks.core.buddy

import org.koin.dsl.module

/** The buddy's voice, its schedule and its face on Today. All stateless. */
val buddyModule = module {
    factory { BuddyVoice() }
    factory { NotificationPlanner(get()) }
    factory { TodayBuddy(get()) }
}
