package io.github.meko123456.dayblocks.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.meko123456.dayblocks.DayBlocksApplication
import io.github.meko123456.dayblocks.composeapp.reminders.ReminderResponder
import io.github.meko123456.dayblocks.core.notifications.ReminderIntents
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Posts a notification when its alarm fires, and records the answer when one of its buttons is
 * tapped — both without the app on screen, often without it running. Not exported: only this
 * app's own PendingIntents can reach it.
 */
class NotificationReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderIntents.readDelivery(intent)?.let { delivery ->
            ReminderNotifications.post(context, delivery)
            return
        }
        val answer = ReminderIntents.readAnswer(intent) ?: return
        ReminderNotifications.dismiss(context, answer.notificationId)
        val pending = goAsync()
        (context.applicationContext as DayBlocksApplication).scope.launch {
            try {
                get<ReminderResponder>().answer(answer.blockId, answer.answer)
            } finally {
                pending.finish()
            }
        }
    }
}
