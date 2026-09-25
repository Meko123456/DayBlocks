package io.github.meko123456.dayblocks.composeapp.di

import io.github.meko123456.dayblocks.composeapp.reminders.remindersModule
import io.github.meko123456.dayblocks.core.buddy.buddyModule
import io.github.meko123456.dayblocks.core.common.commonModule
import io.github.meko123456.dayblocks.core.data.dataModule
import io.github.meko123456.dayblocks.core.database.databaseModule
import io.github.meko123456.dayblocks.core.domain.domainModule
import io.github.meko123456.dayblocks.core.notifications.notificationsModule
import io.github.meko123456.dayblocks.feature.checkin.checkinModule
import io.github.meko123456.dayblocks.feature.editblock.editblockModule
import io.github.meko123456.dayblocks.feature.onboarding.onboardingModule
import io.github.meko123456.dayblocks.feature.settings.settingsModule
import io.github.meko123456.dayblocks.feature.stats.statsModule
import io.github.meko123456.dayblocks.feature.templates.templatesModule
import io.github.meko123456.dayblocks.feature.today.todayModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module

/**
 * The full list, and the only place it exists. Each module ships its own Koin module; this is
 * where they are assembled, which makes :composeApp the single composition root.
 */
fun appModules(): List<Module> = listOf(
    commonModule,
    domainModule,
    databaseModule,
    dataModule,
    notificationsModule,
    buddyModule,
    remindersModule,
    onboardingModule,
    todayModule,
    editblockModule,
    templatesModule,
    checkinModule,
    statsModule,
    settingsModule,
)

/**
 * Starts Koin with the shared modules plus whatever the platform adds — the Android side passes
 * its Context-bound bindings, iOS passes its own.
 */
fun initKoin(platformModules: List<Module> = emptyList(), appDeclaration: KoinApplication.() -> Unit = {}) =
    startKoin {
        appDeclaration()
        modules(appModules() + platformModules)
    }
