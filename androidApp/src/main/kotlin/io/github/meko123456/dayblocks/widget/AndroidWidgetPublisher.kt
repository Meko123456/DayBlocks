package io.github.meko123456.dayblocks.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.meko123456.dayblocks.composeapp.widgets.WidgetPublisher
import io.github.meko123456.dayblocks.composeapp.widgets.WidgetState
import io.github.meko123456.dayblocks.core.notifications.ExactAlarms

/**
 * Redraws every placed widget, then arranges to be back at the next moment the picture changes —
 * the next block boundary, or the rollover. Not a wake-up alarm: a widget on a dark screen is
 * seen by nobody, and the phone waking up is soon enough to redraw it.
 */
class AndroidWidgetPublisher(private val context: Context) : WidgetPublisher {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override suspend fun publish(state: WidgetState) {
        val widget = TodayWidget()
        GlanceAppWidgetManager(context).getGlanceIds(TodayWidget::class.java).forEach { id ->
            updateAppWidgetState(context, id) { prefs -> prefs[TodayWidget.VERSION] = (prefs[TodayWidget.VERSION] ?: 0) + 1 }
            widget.update(context, id)
        }
        val next = state.entries.drop(1).firstOrNull()?.at ?: state.refreshAt
        val refresh = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, WidgetRefreshReceiver::class.java).setAction(WidgetRefreshReceiver.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = next.toEpochMilliseconds()
        try {
            if (ExactAlarms.allowed(context)) alarms.setExact(AlarmManager.RTC, at, refresh) else alarms.setWindow(AlarmManager.RTC, at, WINDOW_MS, refresh)
        } catch (revoked: SecurityException) {
            alarms.setWindow(AlarmManager.RTC, at, WINDOW_MS, refresh)
        }
    }

    private companion object {
        const val WINDOW_MS = 10 * 60 * 1000L
    }
}
