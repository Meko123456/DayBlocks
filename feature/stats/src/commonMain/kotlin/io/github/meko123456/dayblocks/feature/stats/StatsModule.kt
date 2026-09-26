package io.github.meko123456.dayblocks.feature.stats

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val statsModule = module {
    viewModel { StatsViewModel(get(), get(), get(), get(), get(), get()) }
}
