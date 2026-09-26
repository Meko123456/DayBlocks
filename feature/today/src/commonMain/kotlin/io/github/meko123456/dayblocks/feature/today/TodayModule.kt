package io.github.meko123456.dayblocks.feature.today

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The ViewModel's scope is left to its default, so production gets the standard Main-bound one. */
val todayModule = module {
    viewModel { TodayViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}
