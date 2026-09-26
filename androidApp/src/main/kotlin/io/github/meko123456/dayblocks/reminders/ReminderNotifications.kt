package io.github.meko123456.dayblocks.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.meko123456.dayblocks.MainActivity
import io.github.meko123456.dayblocks.R
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind
import io.github.meko123456.dayblocks.core.notifications.Delivery
import io.github.meko123456.dayblocks.core.notifications.ReminderIntents

/**
 * Posting on Android. Every kind of notification has its own channel, so each can be silenced on
 * its own in system settings — someone who wants block starts but not the evening planning nudge
 * can have exactly that. A check-in carries its three answers as buttons.
 */
internal object ReminderNotifications {

    /** Idempotent: creating a channel that exists only updates its name and description. */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            NotificationKind.entries.map { kind ->
                NotificationChannel(kind.channelId, context.getString(kind.channelName), kind.importance).apply {
                    description = context.getString(kind.channelDescription)
                }
            },
        )
    }

    fun post(context: Context, delivery: Delivery) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val builder = NotificationCompat.Builder(context, delivery.kind.channelId)
            .setSmallIcon(R.drawable.ic_stat_buddy)
            .setContentTitle(delivery.title)
            .setContentText(delivery.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(delivery.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (delivery.kind.importance >= NotificationManager.IMPORTANCE_HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openApp(context, delivery))
            .setAutoCancel(true)
        delivery.actions.forEach { answer ->
            builder.addAction(0, context.getString(answer.label), answerIntent(context, delivery, answer))
        }
        NotificationManagerCompat.from(context).notify(delivery.id, NOTIFICATION_ID, builder.build())
    }

    fun dismiss(context: Context, notificationId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId, NOTIFICATION_ID)
    }

    /** Opens the app; the review opens its day's check-in. */
    private fun openApp(context: Context, delivery: Delivery): PendingIntent = PendingIntent.getActivity(
        context, 0,
        ReminderIntents.open(context, MainActivity::class.java, delivery).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun answerIntent(context: Context, delivery: Delivery, answer: CheckInAnswer): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        ReminderIntents.answer(context, NotificationReceiver::class.java, delivery, answer),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Notifications are told apart by their string tag — the notification's own id — rather than by
     * an int derived from it, so two ids can never collide into one notification.
     */
    private const val NOTIFICATION_ID = 1
}

private val NotificationKind.channelId: String
    get() = when (this) {
        NotificationKind.BlockStart -> "block_start"
        NotificationKind.MidBlockCheckIn -> "check_in"
        NotificationKind.Nudge -> "nudge"
        NotificationKind.PlanningReminder -> "planning"
        NotificationKind.Streak -> "streak"
        NotificationKind.Comeback -> "comeback"
        NotificationKind.EndOfDay -> "end_of_day"
    }

@get:StringRes
private val NotificationKind.channelName: Int
    get() = when (this) {
        NotificationKind.BlockStart -> R.string.channel_block_start
        NotificationKind.MidBlockCheckIn -> R.string.channel_check_in
        NotificationKind.Nudge -> R.string.channel_nudge
        NotificationKind.PlanningReminder -> R.string.channel_planning
        NotificationKind.Streak -> R.string.channel_streak
        NotificationKind.Comeback -> R.string.channel_comeback
        NotificationKind.EndOfDay -> R.string.channel_end_of_day
    }

@get:StringRes
private val NotificationKind.channelDescription: Int
    get() = when (this) {
        NotificationKind.BlockStart -> R.string.channel_block_start_description
        NotificationKind.MidBlockCheckIn -> R.string.channel_check_in_description
        NotificationKind.Nudge -> R.string.channel_nudge_description
        NotificationKind.PlanningReminder -> R.string.channel_planning_description
        NotificationKind.Streak -> R.string.channel_streak_description
        NotificationKind.Comeback -> R.string.channel_comeback_description
        NotificationKind.EndOfDay -> R.string.channel_end_of_day_description
    }

/** A block starting and a check-in are the moments that matter, so they may interrupt. */
private val NotificationKind.importance: Int
    get() = when (this) {
        NotificationKind.BlockStart, NotificationKind.MidBlockCheckIn -> NotificationManager.IMPORTANCE_HIGH
        else -> NotificationManager.IMPORTANCE_DEFAULT
    }

@get:StringRes
private val CheckInAnswer.label: Int
    get() = when (this) {
        CheckInAnswer.OnIt -> R.string.answer_on_it
        CheckInAnswer.GotDistracted -> R.string.answer_got_distracted
        CheckInAnswer.SkipBlock -> R.string.answer_skip_block
    }
