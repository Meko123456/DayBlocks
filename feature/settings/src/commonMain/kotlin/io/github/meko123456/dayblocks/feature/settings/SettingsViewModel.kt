package io.github.meko123456.dayblocks.feature.settings

import io.github.meko123456.dayblocks.core.common.TimeProvider
import io.github.meko123456.dayblocks.core.designsystem.mvi.MviViewModel
import io.github.meko123456.dayblocks.core.domain.model.BuddySettings
import io.github.meko123456.dayblocks.core.domain.repository.BackupRepository
import io.github.meko123456.dayblocks.core.domain.repository.ImportResult
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalTime

/**
 * Settings: every change is saved as it is made, and the rescheduler and widgets follow at once —
 * there is nothing to apply and nothing to forget. Import asks first, because it replaces
 * everything.
 */
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val clock: TimeProvider,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState(), scope) {

    init {
        launchInScope {
            combine(settings.observeBuddy(), settings.observeApp()) { buddy, app -> buddy to app }.collect { (buddy, app) ->
                // The draft is the user's while they type; it only starts from the stored name.
                reduce { copy(loading = false, buddy = buddy, app = app, nameDraft = if (loading) buddy.name else nameDraft) }
            }
        }
    }

    override suspend fun handle(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.NameChanged -> {
                val draft = intent.text.take(BuddySettings.MAX_NAME_LENGTH)
                reduce { copy(nameDraft = draft) }
                val name = draft.trim()
                if (name.isNotEmpty()) settings.updateBuddy { it.copy(name = name) }
            }
            is SettingsIntent.TonePicked -> settings.updateBuddy { it.copy(tone = intent.tone) }
            is SettingsIntent.QuietStartMoved -> settings.updateBuddy { it.copy(quietHours = it.quietHours.copy(start = it.quietHours.start.shifted(intent.minutes))) }
            is SettingsIntent.QuietEndMoved -> settings.updateBuddy { it.copy(quietHours = it.quietHours.copy(end = it.quietHours.end.shifted(intent.minutes))) }
            is SettingsIntent.CapMoved -> settings.updateBuddy { it.copy(dailyCap = (it.dailyCap + intent.by).coerceIn(1, BuddySettings.MAX_DAILY_CAP)) }
            is SettingsIntent.TimelineStartMoved -> settings.updateApp {
                it.copy(timelineStartHour = (it.timelineStartHour + intent.hours).coerceIn(0, it.timelineEndHour - 1))
            }
            is SettingsIntent.TimelineEndMoved -> settings.updateApp {
                it.copy(timelineEndHour = (it.timelineEndHour + intent.hours).coerceIn(it.timelineStartHour + 1, 24))
            }
            is SettingsIntent.ThemePicked -> settings.updateApp { it.copy(theme = intent.theme) }
            is SettingsIntent.CategoryColorPicked -> settings.updateApp {
                it.copy(categoryColors = if (intent.argb == null) it.categoryColors - intent.category else it.categoryColors + (intent.category to intent.argb))
            }
            SettingsIntent.ExportTapped -> emit(SettingsEffect.Export("dayblocks-${clock.nowLocal().date}.json", backup.export()))
            SettingsIntent.ImportTapped -> emit(SettingsEffect.ChooseImport)
            is SettingsIntent.ImportChosen -> reduce { copy(confirmingImport = intent.json) }
            SettingsIntent.ImportDismissed -> reduce { copy(confirmingImport = null) }
            SettingsIntent.ImportConfirmed -> {
                val json = state.value.confirmingImport ?: return
                reduce { copy(confirmingImport = null) }
                val message = when (val result = backup.import(json)) {
                    is ImportResult.Imported -> "Restored ${plural(result.blocks, "block")} and ${plural(result.templates, "template")}."
                    is ImportResult.Rejected -> result.reason
                }
                emit(SettingsEffect.Message(message))
            }
            SettingsIntent.BackTapped -> emit(SettingsEffect.Close)
        }
    }
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

/** A clock time moved by [minutes], round the clock. */
private fun LocalTime.shifted(minutes: Int): LocalTime {
    val total = (hour * 60 + minute + minutes).mod(24 * 60)
    return LocalTime(total / 60, total % 60)
}
