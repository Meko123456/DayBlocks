package io.github.meko123456.dayblocks.composeapp.reminders

import org.koin.dsl.module

/**
 * The glue between the planner and the platform. Here in the app shell because it is the one
 * module that sees both :core:buddy and :core:notifications; the platform binds the
 * NotificationScheduler and ClockStyle it needs.
 */
val remindersModule = module {
    single { ReminderRescheduler(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ReminderResponder(get(), get(), get()) }
}
