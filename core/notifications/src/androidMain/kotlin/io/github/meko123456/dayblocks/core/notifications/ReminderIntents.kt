package io.github.meko123456.dayblocks.core.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.meko123456.dayblocks.core.domain.model.BlockId
import io.github.meko123456.dayblocks.core.domain.model.CheckInAnswer
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind
import io.github.meko123456.dayblocks.core.domain.model.ScheduledNotification

/** A notification as it arrives in a receiver: everything needed to post it. */
data class Delivery(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val body: String,
    val blockId: BlockId?,
    val actions: List<CheckInAnswer>,
)

/** A button tapped on a posted notification. */
data class Answer(val notificationId: String, val blockId: BlockId, val answer: CheckInAnswer)

/**
 * How a notification travels on Android: out of the scheduler inside an alarm's intent, into the
 * receiver that posts it, and back from a button as an answer.
 *
 * Everything a notification shows rides in the intent, so posting it needs no database read when
 * the alarm fires — the text is what was planned, and a reschedule replaces it if the plan changes.
 * Each intent's data URI carries the notification's id, because PendingIntents are told apart by
 * action and data, never by extras: that is what makes every alarm its own and not one overwrite.
 */
object ReminderIntents {
    const val ACTION_DELIVER = "io.github.meko123456.dayblocks.action.DELIVER_REMINDER"
    const val ACTION_ANSWER = "io.github.meko123456.dayblocks.action.ANSWER_REMINDER"
    const val ACTION_REFRESH = "io.github.meko123456.dayblocks.action.REFRESH_REMINDERS"

    private const val EXTRA_ID = "id"
    private const val EXTRA_KIND = "kind"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_BODY = "body"
    private const val EXTRA_BLOCK = "block"
    private const val EXTRA_ACTIONS = "actions"
    private const val EXTRA_ANSWER = "answer"

    /** The alarm's intent, without the notification: enough to find and cancel it. */
    fun delivery(context: Context, receiver: Class<*>, id: String): Intent =
        Intent(context, receiver).setAction(ACTION_DELIVER).setData(uri("deliver", id))

    fun delivery(context: Context, receiver: Class<*>, notification: ScheduledNotification): Intent =
        delivery(context, receiver, notification.id)
            .putExtra(EXTRA_ID, notification.id)
            .putExtra(EXTRA_KIND, notification.kind.name)
            .putExtra(EXTRA_TITLE, notification.title)
            .putExtra(EXTRA_BODY, notification.body)
            .putExtra(EXTRA_BLOCK, notification.blockId?.value)
            .putExtra(EXTRA_ACTIONS, notification.actions.map { it.name }.toTypedArray())

    fun answer(context: Context, receiver: Class<*>, delivery: Delivery, answer: CheckInAnswer): Intent =
        Intent(context, receiver)
            .setAction(ACTION_ANSWER)
            .setData(uri("answer", "${delivery.id}/${answer.name}"))
            .putExtra(EXTRA_ID, delivery.id)
            .putExtra(EXTRA_BLOCK, delivery.blockId?.value)
            .putExtra(EXTRA_ANSWER, answer.name)

    /** Null for anything malformed: a receiver must never crash on an intent it cannot read. */
    fun readDelivery(intent: Intent): Delivery? {
        if (intent.action != ACTION_DELIVER) return null
        val id = intent.getStringExtra(EXTRA_ID) ?: return null
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { name -> NotificationKind.entries.firstOrNull { it.name == name } } ?: return null
        return Delivery(
            id = id,
            kind = kind,
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            body = intent.getStringExtra(EXTRA_BODY).orEmpty(),
            blockId = intent.getStringExtra(EXTRA_BLOCK)?.let(::BlockId),
            actions = intent.getStringArrayExtra(EXTRA_ACTIONS).orEmpty().mapNotNull { it.toAnswerOrNull() },
        )
    }

    fun readAnswer(intent: Intent): Answer? {
        if (intent.action != ACTION_ANSWER) return null
        return Answer(
            notificationId = intent.getStringExtra(EXTRA_ID) ?: return null,
            blockId = intent.getStringExtra(EXTRA_BLOCK)?.let(::BlockId) ?: return null,
            answer = intent.getStringExtra(EXTRA_ANSWER)?.toAnswerOrNull() ?: return null,
        )
    }

    private fun uri(kind: String, id: String): Uri =
        Uri.Builder().scheme("dayblocks").authority(kind).appendPath(id).build()

    private fun String.toAnswerOrNull(): CheckInAnswer? = CheckInAnswer.entries.firstOrNull { it.name == this }
}
