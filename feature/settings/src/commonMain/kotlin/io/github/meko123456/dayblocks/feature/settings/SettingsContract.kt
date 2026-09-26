package io.github.meko123456.dayblocks.feature.settings

import io.github.meko123456.dayblocks.core.domain.model.AppSettings
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.ThemeMode

data class SettingsState(
    val loading: Boolean = true,
    val buddy: BuddySettings = BuddySettings(),
    val app: AppSettings = AppSettings(),
    /** The name as typed. Saved whenever it is a name; a blank field keeps the last one. */
    val nameDraft: String = BuddySettings.DEFAULT_NAME,
    /** A backup chosen for import, waiting for "replace everything?" to be answered. */
    val confirmingImport: String? = null,
)

sealed interface SettingsIntent {
    data class NameChanged(val text: String) : SettingsIntent
    data class TonePicked(val tone: BuddyTone) : SettingsIntent
    data class QuietStartMoved(val minutes: Int) : SettingsIntent
    data class QuietEndMoved(val minutes: Int) : SettingsIntent
    data class CapMoved(val by: Int) : SettingsIntent
    data class TimelineStartMoved(val hours: Int) : SettingsIntent
    data class TimelineEndMoved(val hours: Int) : SettingsIntent
    data class ThemePicked(val theme: ThemeMode) : SettingsIntent
    /** Null goes back to the category's own colour. */
    data class CategoryColorPicked(val category: Category, val argb: Long?) : SettingsIntent
    data object ExportTapped : SettingsIntent
    data object ImportTapped : SettingsIntent
    data class ImportChosen(val json: String) : SettingsIntent
    data object ImportConfirmed : SettingsIntent
    data object ImportDismissed : SettingsIntent
    data object BackTapped : SettingsIntent
}

sealed interface SettingsEffect {
    data object Close : SettingsEffect
    data class Export(val fileName: String, val json: String) : SettingsEffect
    data object ChooseImport : SettingsEffect
    data class Message(val text: String) : SettingsEffect
}

/**
 * Where a backup is saved to and read from: a document the user picks, on each platform in its own
 * way. The app shell implements it; this screen only says when.
 */
interface BackupFiles {
    fun save(fileName: String, json: String)

    fun open(onOpened: (String) -> Unit)
}
