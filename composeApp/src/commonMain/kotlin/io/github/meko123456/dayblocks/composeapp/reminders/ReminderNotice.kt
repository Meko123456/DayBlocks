package io.github.meko123456.dayblocks.composeapp.reminders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.meko123456.dayblocks.core.buddy.DEFAULT_BUDDY_NAME
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * A strip on Today that appears only when reminders cannot reach the user: notifications are off,
 * or — on Android 12 and later — exact alarms are not allowed, so every reminder could arrive
 * minutes late. It says what is missing and fixes it in one tap. "Later" hides it until the next
 * launch; onboarding will ask properly, with the explanation first.
 */
@Composable
fun ReminderNotice(modifier: Modifier = Modifier) {
    val rescheduler = koinInject<ReminderRescheduler>()
    val scope = rememberCoroutineScope()
    val access = rememberReminderAccess(onGranted = { scope.launch { rescheduler.rescheduleNow() } })
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (dismissed) return

    val notice = when {
        access.notificationsAllowed == false -> Notice(
            title = "$DEFAULT_BUDDY_NAME can't reach you",
            text = "Turn on notifications and $DEFAULT_BUDDY_NAME will tell you when a block starts, and check in halfway through.",
            action = "Turn on",
            onAction = access::requestNotifications,
        )
        access.notificationsAllowed == true && !access.exactTimingAllowed -> Notice(
            title = "Reminders may run late",
            text = "Allow alarms & reminders so $DEFAULT_BUDDY_NAME is on time to the minute, not whenever the phone next wakes.",
            action = "Allow",
            onAction = access::openExactTimingSettings,
        )
        else -> return
    }

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(notice.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(notice.text, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = notice.onAction) { Text(notice.action) }
                TextButton(onClick = { dismissed = true }) { Text("Later") }
            }
        }
    }
}

private class Notice(val title: String, val text: String, val action: String, val onAction: () -> Unit)
