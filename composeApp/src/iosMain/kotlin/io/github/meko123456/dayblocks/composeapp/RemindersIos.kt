package io.github.meko123456.dayblocks.composeapp

import io.github.meko123456.dayblocks.composeapp.navigation.AppLink
import io.github.meko123456.dayblocks.composeapp.navigation.AppLinks
import io.github.meko123456.dayblocks.composeapp.reminders.ReminderRescheduler
import io.github.meko123456.dayblocks.composeapp.reminders.ReminderResponder
import io.github.meko123456.dayblocks.composeapp.widgets.WidgetUpdater
import io.github.meko123456.dayblocks.composeapp.widgets.widgetReloader
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.notifications.registerNotificationCategories
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.mp.KoinPlatform
import platform.Foundation.NSLog

/*
 * The reminder entry points Swift calls. Plain functions with a completion callback rather than
 * suspend functions: the notification delegate and the background refresh run on threads of the
 * system's choosing, and a Kotlin suspend function exported to Swift may only be called from the
 * main one.
 */

private val reminders = CoroutineScope(
    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
        // No format arguments: a Kotlin String through NSLog's C varargs crashes Foundation.
        NSLog("DayBlocks reminders failed: $error".replace("%", "%%"))
    },
)

/** Registers the check-in's buttons and starts following the plan. Once, from [doInitKoin]. */
internal fun startReminders() {
    registerNotificationCategories()
    KoinPlatform.getKoin().get<ReminderRescheduler>().start(reminders)
    KoinPlatform.getKoin().get<WidgetUpdater>().start(reminders)
}

/** Whenever the app comes to the foreground, the clock changes, or iOS grants a background refresh. */
fun rescheduleReminders(done: () -> Unit) {
    reminders.launch {
        try {
            KoinPlatform.getKoin().get<ReminderRescheduler>().rescheduleNow()
        } finally {
            done()
        }
    }
}

/** When one of a check-in's buttons is tapped. [answer] is the button's identifier: an answer's name. */
fun answerReminder(blockId: String, answer: String, done: () -> Unit) {
    val parsed = CheckInAnswer.entries.firstOrNull { it.name == answer }
    if (parsed == null) {
        done()
        return
    }
    reminders.launch {
        try {
            KoinPlatform.getKoin().get<ReminderResponder>().answer(BlockId(blockId), parsed)
        } finally {
            done()
        }
    }
}

/** When the review notification itself is tapped: open that day's check-in. [date] is ISO, or null. */
fun openReview(date: String?) {
    AppLinks.open(AppLink.Review(date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }))
}

/** Swift hands over how to reload the widget; WidgetCenter is Swift-only. Set before [doInitKoin]. */
fun setWidgetReloader(reload: () -> Unit) {
    widgetReloader = reload
}

/** The widget was tapped: open Today, whatever screen the app was left on. */
fun openToday() {
    AppLinks.open(AppLink.Today)
}
