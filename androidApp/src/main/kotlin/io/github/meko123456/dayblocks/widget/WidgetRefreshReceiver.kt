package io.github.meko123456.dayblocks.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.meko123456.dayblocks.DayBlocksApplication
import io.github.meko123456.dayblocks.composeapp.widgets.WidgetUpdater
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Redraws the widgets and sets the next alarm when a block boundary or the rollover comes, and when
 * the clock, the time zone or the language changes. Those put every boundary at another instant and
 * change how its time reads, and a running process otherwise kept the old ones: after Tbilisi to
 * Tokyo, the widget's 23:00 rollover alarm stayed where it was, at 04:00 Tokyo time.
 *
 * Not exported: the system's broadcasts reach a receiver that is not exported, and the refresh
 * itself only ever comes from DayBlocks' own alarm.
 */
class WidgetRefreshReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        val pending = goAsync()
        (context.applicationContext as DayBlocksApplication).scope.launch {
            try {
                get<WidgetUpdater>().refreshNow()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "io.github.meko123456.dayblocks.action.REFRESH_WIDGETS"

        private val HANDLED = setOf(
            ACTION_REFRESH,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
        )
    }
}
