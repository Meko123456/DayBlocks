package io.github.meko123456.dayblocks.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.meko123456.dayblocks.MainActivity
import io.github.meko123456.dayblocks.composeapp.widgets.WidgetUpdater
import io.github.meko123456.dayblocks.core.buddy.WidgetEntry
import io.github.meko123456.dayblocks.core.designsystem.buddy.renderBuddy
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Today on the home screen. Small: the block that is on, until when, and the buddy's face.
 * Medium: what is next, too, and how far through the day's plan it is. Tapping opens Today.
 *
 * The one image is the buddy, drawn by the app's own code into a small bitmap: RemoteViews have a
 * memory cap, and a face of a few kilobytes keeps well inside it.
 */
class TodayWidget : GlanceAppWidget(), KoinComponent {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val updater = get<WidgetUpdater>()
        val first = updater.build()
        val open = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_TODAY, true)
        provideContent {
            // A session that is already open when the plan changes is recomposed, not restarted,
            // so the data is read here, again for every new version the publisher writes.
            val version = currentState(VERSION) ?: 0
            val state by produceState(first, version) { value = updater.build() }
            val entry = state.entries.first()
            val face = remember(entry.mood) { ImageProvider(renderBuddy(entry.mood, FACE_PX).asAndroidBitmap()) }
            Content(entry, state.buddyName, face, open)
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)

        /** Enough for the largest face the medium size draws, and no more. */
        private const val FACE_PX = 168

        /** Bumped by every publish; a new value makes an open session read the plan again. */
        val VERSION = intPreferencesKey("version")
    }
}

private val Paper = Color(0xFFFAF7F2)
private val Ink = Color(0xFF1B1D22)
private val Sunrise = Color(0xFFF2994A)
private val background = ColorProvider(day = Paper, night = Ink)
private val text = ColorProvider(day = Ink, night = Paper)
private val quiet = ColorProvider(day = Ink.copy(alpha = 0.6f), night = Paper.copy(alpha = 0.6f))
private val accent = ColorProvider(day = Sunrise, night = Sunrise)
private val track = ColorProvider(day = Ink.copy(alpha = 0.12f), night = Paper.copy(alpha = 0.18f))

@Composable
private fun Content(entry: WidgetEntry, name: String, face: ImageProvider, open: Intent) {
    val medium = LocalSize.current.width >= TodayWidget.MEDIUM.width
    val now = entry.current?.title ?: "Free right now"
    val detail = entry.current?.let { "until ${it.endClock}" }
        ?: entry.next?.let { "Next: ${it.title} at ${it.startClock}" }
        ?: "Nothing else planned today"
    Row(
        GlanceModifier.fillMaxSize().background(background).cornerRadius(20.dp).padding(12.dp).clickable(actionStartActivity(open)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (medium) {
            Image(face, contentDescription = "$name, ${entry.mood.name.lowercase()}", modifier = GlanceModifier.size(64.dp))
            Spacer(GlanceModifier.width(12.dp))
        }
        Column(GlanceModifier.fillMaxWidth()) {
            if (!medium) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(face, contentDescription = "$name, ${entry.mood.name.lowercase()}", modifier = GlanceModifier.size(32.dp))
                    Spacer(GlanceModifier.width(6.dp))
                    Text(name, style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                }
                Spacer(GlanceModifier.height(6.dp))
            } else {
                Text("NOW", style = TextStyle(color = accent, fontWeight = FontWeight.Bold, fontSize = 11.sp))
            }
            Text(now, maxLines = 2, style = TextStyle(color = text, fontWeight = FontWeight.Bold, fontSize = 15.sp))
            Text(detail, maxLines = 2, style = TextStyle(color = quiet, fontSize = 12.sp))
            if (medium) {
                entry.current?.let { entry.next }?.let { next ->
                    Text("Then ${next.title} at ${next.startClock}", maxLines = 1, style = TextStyle(color = quiet, fontSize = 12.sp))
                }
                Spacer(GlanceModifier.height(8.dp))
                if (entry.planned > 0) {
                    LinearProgressIndicator(
                        progress = entry.progress,
                        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                        color = accent,
                        backgroundColor = track,
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text("${entry.done} of ${entry.planned} ${if (entry.planned == 1) "block" else "blocks"} done", style = TextStyle(color = quiet, fontSize = 11.sp))
                }
            }
        }
    }
}
