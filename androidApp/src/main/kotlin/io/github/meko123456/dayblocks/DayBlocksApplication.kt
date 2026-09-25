package io.github.meko123456.dayblocks

import android.app.Application
import io.github.meko123456.dayblocks.composeapp.di.initKoin
import io.github.meko123456.dayblocks.core.database.DriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Starts Koin once per process with the shared modules plus the bindings only Android can supply.
 * The notification receivers and the widget are started by the system without an Activity, so
 * the graph has to exist from Application.onCreate rather than from the first screen.
 */
class DayBlocksApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(platformModules = listOf(androidModule)) {
            androidContext(this@DayBlocksApplication)
        }
    }
}

private val androidModule = module {
    single { DriverFactory(get()) }
}
