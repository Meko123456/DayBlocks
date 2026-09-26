package io.github.meko123456.dayblocks.composeapp

import io.github.meko123456.dayblocks.composeapp.di.initKoin
import io.github.meko123456.dayblocks.composeapp.widgets.IosWidgetPublisher
import io.github.meko123456.dayblocks.composeapp.widgets.WidgetPublisher
import io.github.meko123456.dayblocks.core.common.ClockStyle
import io.github.meko123456.dayblocks.core.common.IosClockStyle
import io.github.meko123456.dayblocks.core.data.SettingsFactory
import io.github.meko123456.dayblocks.core.database.DriverFactory
import io.github.meko123456.dayblocks.core.notifications.NotificationScheduler
import io.github.meko123456.dayblocks.core.notifications.UserNotificationScheduler
import org.koin.dsl.module

/**
 * Called once from the SwiftUI App's init.
 *
 * [inMemoryDatabase] is the UI tests' isolation seam: each run starts from an empty plan and default
 * settings instead of whatever the previous run left on the simulator, which would make "add a block and see it"
 * pass or fail depending on history. It is a required parameter rather than a default, because a
 * Kotlin default argument does not reach the Objective-C header — Swift would not see it at all.
 */
fun doInitKoin(inMemoryDatabase: Boolean) {
    val driverFactory = if (inMemoryDatabase) DriverFactory(name = null) else DriverFactory()
    initKoin(
        platformModules = listOf(
            module {
                single { driverFactory }
                single { SettingsFactory(fresh = inMemoryDatabase) }
                single<ClockStyle> { IosClockStyle() }
                single<NotificationScheduler> { UserNotificationScheduler() }
                single<WidgetPublisher> { IosWidgetPublisher() }
            },
        ),
    )
    startReminders()
}
