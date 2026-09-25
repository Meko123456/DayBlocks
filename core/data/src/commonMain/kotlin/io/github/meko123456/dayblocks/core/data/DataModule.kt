package io.github.meko123456.dayblocks.core.data

import org.koin.dsl.module

/**
 * Repository implementations — the only place that knows SQLDelight and the settings store exist.
 * The interfaces they satisfy live in :core:domain, which is why no feature module can reach this
 * one: features depend on the domain, and the binding is resolved at startup in :composeApp.
 */
val dataModule = module {
}
