package io.github.meko123456.dayblocks

import android.app.Application
import android.util.Log
import io.github.meko123456.dayblocks.composeapp.di.initKoin
import io.github.meko123456.dayblocks.composeapp.reminders.ReminderRescheduler
import io.github.meko123456.dayblocks.composeapp.widgets.WidgetPublisher
import io.github.meko123456.dayblocks.composeapp.widgets.WidgetUpdater
import io.github.meko123456.dayblocks.core.common.AndroidClockStyle
import io.github.meko123456.dayblocks.core.common.ClockStyle
import io.github.meko123456.dayblocks.core.data.SettingsFactory
import io.github.meko123456.dayblocks.core.database.DriverFactory
import io.github.meko123456.dayblocks.core.notifications.AlarmNotificationScheduler
import io.github.meko123456.dayblocks.core.notifications.NotificationScheduler
import io.github.meko123456.dayblocks.reminders.NotificationReceiver
import io.github.meko123456.dayblocks.reminders.ReminderNotifications
import io.github.meko123456.dayblocks.reminders.RescheduleReceiver
import io.github.meko123456.dayblocks.widget.AndroidWidgetPublisher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/**
 * Starts Koin once per process with the shared modules plus the bindings only Android can supply.
 * The notification receivers and the widget are started by the system without an Activity, so
 * the graph has to exist from Application.onCreate rather than from the first screen.
 *
 * Every process start — the app opened, an alarm firing, a reboot — also starts the rescheduler
 * and the widget updater, which bring notifications and widgets up to date at once and then follow
 * the plan for as long as the process lives.
 */
class DayBlocksApplication : Application() {

    /** Outlives every screen. Receivers finish their work here, and the rescheduler runs here. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e(TAG, "Reminder work failed", error)
        },
    )

    override fun onCreate() {
        super.onCreate()
        initKoin(platformModules = listOf(androidModule)) {
            androidContext(this@DayBlocksApplication)
        }
        ReminderNotifications.createChannels(this)
        GlobalContext.get().get<ReminderRescheduler>().start(scope)
        GlobalContext.get().get<WidgetUpdater>().start(scope)
    }

    private companion object {
        const val TAG = "DayBlocks"
    }
}

private val androidModule = module {
    single { DriverFactory(get()) }
    single { SettingsFactory(androidContext()) }
    single<ClockStyle> { AndroidClockStyle(androidContext()) }
    single<WidgetPublisher> { AndroidWidgetPublisher(androidContext()) }
    single<NotificationScheduler> {
        AlarmNotificationScheduler(androidContext(), NotificationReceiver::class.java, RescheduleReceiver::class.java)
    }
}
