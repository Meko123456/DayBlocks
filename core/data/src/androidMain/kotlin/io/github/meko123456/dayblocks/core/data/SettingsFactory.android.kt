package io.github.meko123456.dayblocks.core.data

import android.content.Context
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings

actual class SettingsFactory(private val context: Context) {
    internal actual fun create(): ObservableSettings =
        SharedPreferencesSettings(context.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE))

    private companion object {
        const val SETTINGS_FILE = "dayblocks_settings"
    }
}
