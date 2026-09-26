package io.github.meko123456.dayblocks.composeapp.widgets

import androidx.compose.ui.graphics.asSkiaBitmap
import io.github.meko123456.dayblocks.core.buddy.WidgetBlock
import io.github.meko123456.dayblocks.core.designsystem.buddy.renderBuddy
import io.github.meko123456.dayblocks.core.domain.model.BuddyMood
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSLog
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL

/**
 * The widget extension is a separate process that cannot open the app's database, so the app
 * hands it everything it will show: the timeline as `widget.json`, and the buddy's six faces as
 * PNGs drawn by the same code as in the app, all in the App Group container both can read.
 * Then WidgetKit is asked to reload — through Swift, since WidgetCenter has no Objective-C face.
 */
class IosWidgetPublisher : WidgetPublisher {
    private var facesWritten = false

    override suspend fun publish(state: WidgetState) {
        val container = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP)
        if (container == null) {
            NSLog("DayBlocks widgets: no App Group container; the widget cannot be reached")
            return
        }
        // Once per process: an update that changes the drawing restarts the process, so the faces
        // are redrawn exactly when they can have changed.
        if (!facesWritten) {
            BuddyMood.entries.forEach { mood -> container.file("kubi-${mood.name}.png").write(face(mood)) }
            facesWritten = true
        }
        container.file(TIMELINE_FILE).write(Json.encodeToString(WidgetFile.from(state)).encodeToByteArray())
        widgetReloader?.invoke()
    }

    private fun face(mood: BuddyMood): ByteArray =
        Image.makeFromBitmap(renderBuddy(mood, FACE_PX).asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: ByteArray(0)

    private fun NSURL.file(name: String): NSURL = URLByAppendingPathComponent(name)!!

    @OptIn(ExperimentalForeignApi::class)
    private fun NSURL.write(bytes: ByteArray) {
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        data.writeToURL(this, atomically = true)
    }

    companion object {
        /** Shared with the widget extension; both targets carry it in their entitlements. */
        const val APP_GROUP = "group.io.github.meko123456.dayblocks"
        const val TIMELINE_FILE = "widget.json"
        private const val FACE_PX = 180
    }
}

/** Set from Swift at launch: asks WidgetKit to reload the widget's timeline. */
internal var widgetReloader: (() -> Unit)? = null

/** The file the widget extension reads. Times are epoch seconds, which Swift's Date takes as is. */
@Serializable
internal data class WidgetFile(val buddyName: String, val refreshAt: Long, val entries: List<Entry>) {
    @Serializable
    data class Entry(val at: Long, val current: Block?, val next: Block?, val done: Int, val planned: Int, val mood: String)

    @Serializable
    data class Block(val title: String, val category: String, val starts: Long, val ends: Long, val startClock: String, val endClock: String)

    companion object {
        fun from(state: WidgetState) = WidgetFile(
            buddyName = state.buddyName,
            refreshAt = state.refreshAt.epochSeconds,
            entries = state.entries.map { e ->
                Entry(e.at.epochSeconds, e.current?.toFile(), e.next?.toFile(), e.done, e.planned, e.mood.name)
            },
        )

        private fun WidgetBlock.toFile() = Block(title, category.name, starts.epochSeconds, ends.epochSeconds, startClock, endClock)
    }
}
