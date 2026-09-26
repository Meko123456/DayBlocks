package io.github.meko123456.dayblocks.core.data

import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.ObservableSettings
import platform.Foundation.NSUserDefaults

/**
 * @param fresh start from nothing: the UI tests' seam, like the in-memory database. Settings then
 *   live in a suite of their own, emptied at launch, so no run sees what an earlier one chose.
 */
actual class SettingsFactory(private val fresh: Boolean = false) {
    internal actual fun create(): ObservableSettings {
        if (!fresh) return NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults)
        NSUserDefaults.standardUserDefaults.removePersistentDomainForName(FRESH_SUITE)
        return NSUserDefaultsSettings(NSUserDefaults(suiteName = FRESH_SUITE))
    }

    private companion object {
        const val FRESH_SUITE = "io.github.meko123456.dayblocks.fresh"
    }
}
