package io.github.meko123456.dayblocks.composeapp.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.meko123456.dayblocks.feature.settings.BackupFiles

/**
 * The Storage Access Framework: the user chooses where the backup goes and where it comes from,
 * and the app needs no storage permission for either.
 */
@Composable
actual fun rememberBackupFiles(): BackupFiles {
    val context = LocalContext.current
    val pending = remember { Pending() }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = pending.json.also { pending.json = null }
        if (uri != null && json != null) context.contentResolver.openOutputStream(uri)?.use { it.write(json.encodeToByteArray()) }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val onOpened = pending.onOpened.also { pending.onOpened = null }
        if (uri != null && onOpened != null) {
            context.contentResolver.openInputStream(uri)?.use { onOpened(it.readBytes().decodeToString()) }
        }
    }
    return remember(saver, opener) {
        object : BackupFiles {
            override fun save(fileName: String, json: String) {
                pending.json = json
                saver.launch(fileName)
            }

            override fun open(onOpened: (String) -> Unit) {
                pending.onOpened = onOpened
                // Some file managers label JSON as plain text or as bytes; the import checks the contents.
                opener.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
        }
    }
}

private class Pending {
    var json: String? = null
    var onOpened: ((String) -> Unit)? = null
}
