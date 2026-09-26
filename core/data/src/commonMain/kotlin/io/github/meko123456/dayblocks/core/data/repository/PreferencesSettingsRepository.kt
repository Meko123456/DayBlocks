@file:OptIn(ExperimentalSettingsApi::class)

package io.github.meko123456.dayblocks.core.data.repository

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanOrNullFlow
import com.russhwolf.settings.coroutines.getIntOrNullFlow
import com.russhwolf.settings.coroutines.getLongOrNullFlow
import com.russhwolf.settings.coroutines.getStringOrNullFlow
import io.github.meko123456.dayblocks.core.domain.model.AppSettings
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.model.BuddyTone
import io.github.meko123456.dayblocks.core.domain.model.Category
import io.github.meko123456.dayblocks.core.domain.model.QuietHours
import io.github.meko123456.dayblocks.core.domain.model.ThemeMode
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalTime

/**
 * [SettingsRepository] on multiplatform-settings: SharedPreferences on Android, NSUserDefaults on
 * iOS. One key per field, so a value written by a later version and not understood by this one is
 * ignored rather than breaking the rest.
 *
 * Reads are forgiving. Anything missing, unknown or out of range falls back to its default: a
 * settings file is edited by nothing but this app, but a buddy that refuses to start because of
 * one bad value would be the worst possible failure for the feature the app is built around.
 */
internal class PreferencesSettingsRepository(private val settings: ObservableSettings) : SettingsRepository {

    override fun observeBuddy(): Flow<BuddySettings> = combine(
        settings.getStringOrNullFlow(KEY_NAME),
        settings.getStringOrNullFlow(KEY_TONE),
        settings.getIntOrNullFlow(KEY_QUIET_START),
        settings.getIntOrNullFlow(KEY_QUIET_END),
        settings.getIntOrNullFlow(KEY_DAILY_CAP),
    ) { name, tone, quietStart, quietEnd, cap -> read(name, tone, quietStart, quietEnd, cap) }
        .distinctUntilChanged()

    override suspend fun updateBuddy(change: (BuddySettings) -> BuddySettings) {
        val next = change(observeBuddy().first())
        settings.putString(KEY_NAME, next.name)
        settings.putString(KEY_TONE, next.tone.name)
        settings.putInt(KEY_QUIET_START, next.quietHours.start.minuteOfDay)
        settings.putInt(KEY_QUIET_END, next.quietHours.end.minuteOfDay)
        settings.putInt(KEY_DAILY_CAP, next.dailyCap)
    }

    override fun observeApp(): Flow<AppSettings> = combine<Any?, AppSettings>(
        listOf<Flow<Any?>>(
            settings.getStringOrNullFlow(KEY_THEME),
            settings.getIntOrNullFlow(KEY_TIMELINE_START),
            settings.getIntOrNullFlow(KEY_TIMELINE_END),
            settings.getBooleanOrNullFlow(KEY_ONBOARDED),
        ) + Category.entries.map { settings.getLongOrNullFlow(colorKey(it)) },
    ) { values ->
        val defaults = AppSettings()
        val start = (values[1] as Int?)?.takeIf { it in 0..23 }
        val end = (values[2] as Int?)?.takeIf { it in 1..24 }
        val (timelineStart, timelineEnd) = if (start != null && end != null && start < end) start to end else defaults.timelineStartHour to defaults.timelineEndHour
        AppSettings(
            theme = ThemeMode.entries.firstOrNull { it.name == values[0] } ?: defaults.theme,
            timelineStartHour = timelineStart,
            timelineEndHour = timelineEnd,
            categoryColors = Category.entries.mapIndexedNotNull { i, category -> (values[4 + i] as Long?)?.let { category to it } }.toMap(),
            onboarded = values[3] as Boolean? ?: defaults.onboarded,
        )
    }.distinctUntilChanged()

    override suspend fun updateApp(change: (AppSettings) -> AppSettings) {
        val next = change(observeApp().first())
        settings.putString(KEY_THEME, next.theme.name)
        settings.putInt(KEY_TIMELINE_START, next.timelineStartHour)
        settings.putInt(KEY_TIMELINE_END, next.timelineEndHour)
        settings.putBoolean(KEY_ONBOARDED, next.onboarded)
        Category.entries.forEach { category ->
            val color = next.categoryColors[category]
            if (color == null) settings.remove(colorKey(category)) else settings.putLong(colorKey(category), color)
        }
    }

    override fun observeLastOpened(): Flow<Instant?> =
        settings.getLongOrNullFlow(KEY_LAST_OPENED).map { millis -> millis?.let(Instant::fromEpochMilliseconds) }

    override suspend fun markOpened(at: Instant) {
        settings.putLong(KEY_LAST_OPENED, at.toEpochMilliseconds())
    }

    private fun read(name: String?, tone: String?, quietStart: Int?, quietEnd: Int?, cap: Int?): BuddySettings {
        val defaults = BuddySettings()
        return BuddySettings(
            name = name?.trim()?.takeIf { it.isNotEmpty() && it.length <= BuddySettings.MAX_NAME_LENGTH } ?: defaults.name,
            tone = BuddyTone.entries.firstOrNull { it.name == tone } ?: defaults.tone,
            quietHours = QuietHours(
                start = quietStart.toTimeOrNull() ?: defaults.quietHours.start,
                end = quietEnd.toTimeOrNull() ?: defaults.quietHours.end,
            ),
            dailyCap = cap?.takeIf { it in 1..BuddySettings.MAX_DAILY_CAP } ?: defaults.dailyCap,
        )
    }

    private companion object {
        const val KEY_NAME = "buddy.name"
        const val KEY_TONE = "buddy.tone"
        const val KEY_QUIET_START = "buddy.quietHours.start"
        const val KEY_QUIET_END = "buddy.quietHours.end"
        const val KEY_DAILY_CAP = "buddy.dailyCap"
        const val KEY_LAST_OPENED = "app.lastOpened"
        const val KEY_THEME = "app.theme"
        const val KEY_TIMELINE_START = "app.timeline.startHour"
        const val KEY_TIMELINE_END = "app.timeline.endHour"
        const val KEY_ONBOARDED = "app.onboarded"

        fun colorKey(category: Category) = "app.color.${category.name}"
    }
}

private val LocalTime.minuteOfDay: Int get() = hour * 60 + minute

private fun Int?.toTimeOrNull(): LocalTime? = this?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime(it / 60, it % 60) }
