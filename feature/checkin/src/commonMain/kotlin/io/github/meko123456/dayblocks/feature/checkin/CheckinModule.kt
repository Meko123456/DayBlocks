package io.github.meko123456.dayblocks.feature.checkin

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The day to review is the screen's argument; everything else comes from the graph. */
val checkinModule = module {
    viewModel { (args: CheckinArgs) -> CheckinViewModel(args, get(), get(), get(), get(), get(), get(), get()) }
}
