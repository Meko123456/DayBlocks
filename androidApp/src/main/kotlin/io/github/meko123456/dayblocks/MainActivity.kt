package io.github.meko123456.dayblocks

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.meko123456.dayblocks.composeapp.App
import io.github.meko123456.dayblocks.composeapp.navigation.AppLink
import io.github.meko123456.dayblocks.composeapp.navigation.AppLinks
import io.github.meko123456.dayblocks.core.domain.model.NotificationKind
import io.github.meko123456.dayblocks.core.notifications.ReminderIntents

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only a fresh launch: after a rotation the same intent would open the review again.
        if (savedInstanceState == null) openFrom(intent)
        setContent { App() }
    }

    /** A notification tapped while the app is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    private fun openFrom(intent: Intent?) {
        val request = ReminderIntents.readOpen(intent) ?: return
        if (request.kind == NotificationKind.EndOfDay) AppLinks.open(AppLink.Review(request.date))
    }
}
