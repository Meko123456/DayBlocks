package io.github.meko123456.dayblocks.core.data

import com.russhwolf.settings.ObservableSettings

/**
 * Where settings live needs a `Context` on Android and nothing on iOS, so, like the database
 * driver, this is the one piece of the settings store that cannot be common.
 */
expect class SettingsFactory {
    internal fun create(): ObservableSettings
}
