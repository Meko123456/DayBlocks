package io.github.meko123456.dayblocks.core.buddy

import org.koin.dsl.module

/** The buddy's voice, its schedule, its face on Today, at the check-in and in the widgets. All stateless. */
val buddyModule = module {
    factory { BuddyVoice() }
    factory { NotificationPlanner(get()) }
    factory { TodayBuddy(get()) }
    factory { CheckInBuddy(get()) }
    factory { WidgetTimeline(get(), get(), get()) }
}
