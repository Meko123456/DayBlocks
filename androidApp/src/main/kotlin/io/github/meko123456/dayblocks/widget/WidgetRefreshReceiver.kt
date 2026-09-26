package io.github.meko123456.dayblocks.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.meko123456.dayblocks.DayBlocksApplication
import io.github.meko123456.dayblocks.composeapp.widgets.WidgetUpdater
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/** A block boundary or the rollover has come: redraw the widgets and set the next alarm. */
class WidgetRefreshReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REFRESH) return
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
    }
}
