package io.github.meko123456.dayblocks.composeapp.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.meko123456.dayblocks.feature.settings.BackupFiles
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfURL
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeJSON
import platform.darwin.NSObject

/** Save through the share sheet, which offers Files, AirDrop and the rest; open through the Files picker. */
@Composable
actual fun rememberBackupFiles(): BackupFiles = remember { IosBackupFiles() }

private class IosBackupFiles : BackupFiles {
    /** UIDocumentPickerViewController holds its delegate weakly; this keeps it alive while it is up. */
    private var picking: PickerDelegate? = null

    @OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
    override fun save(fileName: String, json: String) {
        val file = NSURL.fileURLWithPath(NSTemporaryDirectory() + fileName)
        NSString.create(string = json).writeToURL(file, atomically = true, encoding = NSUTF8StringEncoding, error = null)
        present(UIActivityViewController(activityItems = listOf(file), applicationActivities = null))
    }

    override fun open(onOpened: (String) -> Unit) {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON), asCopy = true)
        val delegate = PickerDelegate { url ->
            picking = null
            read(url)?.let(onOpened)
        }
        picking = delegate
        picker.delegate = delegate
        present(picker)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun read(url: NSURL): String? = NSString.stringWithContentsOfURL(url, encoding = NSUTF8StringEncoding, error = null)

    /** On top of whatever is showing, so it works from any screen. */
    private fun present(controller: UIViewController) {
        var top = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        top?.presentViewController(controller, animated = true, completion = null)
    }
}

private class PickerDelegate(private val onPicked: (NSURL) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        (didPickDocumentsAtURLs.firstOrNull() as? NSURL)?.let(onPicked)
    }
}
