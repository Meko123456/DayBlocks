package io.github.meko123456.dayblocks.core.data

import com.russhwolf.settings.ObservableSettings
import io.github.meko123456.dayblocks.core.data.repository.PreferencesSettingsRepository
import io.github.meko123456.dayblocks.core.data.repository.SqlBlockRepository
import io.github.meko123456.dayblocks.core.data.repository.SqlOutcomeRepository
import io.github.meko123456.dayblocks.core.data.repository.SqlTemplateRepository
import io.github.meko123456.dayblocks.core.domain.repository.BlockRepository
import io.github.meko123456.dayblocks.core.domain.repository.OutcomeRepository
import io.github.meko123456.dayblocks.core.domain.repository.SettingsRepository
import io.github.meko123456.dayblocks.core.domain.repository.TemplateRepository
import org.koin.dsl.module

/**
 * Repository implementations — the only place that knows SQLDelight and the settings store exist.
 * The interfaces they satisfy live in :core:domain, which is why no feature module can reach this
 * one: features depend on the domain, and the binding is resolved at startup in :composeApp.
 *
 * Singletons because each holds nothing but the database, which is itself a singleton in
 * databaseModule; the dispatchers and the clock come from commonModule.
 */
val dataModule = module {
    single<BlockRepository> { SqlBlockRepository(database = get(), dispatchers = get(), time = get()) }
    single<TemplateRepository> { SqlTemplateRepository(database = get(), dispatchers = get()) }
    single<OutcomeRepository> { SqlOutcomeRepository(database = get(), dispatchers = get()) }
    // The store itself is bound on its own so a test can put an in-memory one in its place.
    single<ObservableSettings> { get<SettingsFactory>().create() }
    single<SettingsRepository> { PreferencesSettingsRepository(get()) }
}
