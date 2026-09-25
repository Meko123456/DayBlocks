package io.github.meko123456.dayblocks.composeapp

import io.github.meko123456.dayblocks.composeapp.di.initKoin
import io.github.meko123456.dayblocks.core.database.DriverFactory
import org.koin.dsl.module

/**
 * Called once from the SwiftUI App's init. Named without a `Koin` type in the signature because
 * a Kotlin default argument does not reach the Objective-C header — Swift sees a plain, required
 * no-argument function.
 */
fun doInitKoin() {
    initKoin(platformModules = listOf(module { single { DriverFactory() } }))
}
