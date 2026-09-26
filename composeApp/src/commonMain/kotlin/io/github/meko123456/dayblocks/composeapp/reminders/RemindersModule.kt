package io.github.meko123456.dayblocks.composeapp.reminders

import io.github.meko123456.dayblocks.composeapp.widgets.WidgetUpdater
import org.koin.dsl.module

/**
 * The glue between the buddy and the platform: notifications and widgets. Here in the app shell
 * because it is the one module that sees both :core:buddy and :core:notifications; the platform
 * binds the NotificationScheduler, WidgetPublisher and ClockStyle it needs.
 */
val remindersModule = module {
    single { ReminderRescheduler(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    factory { ReminderResponder(get(), get(), get()) }
    single { WidgetUpdater(get(), get(), get(), get(), get(), get(), get(), get()) }
}
