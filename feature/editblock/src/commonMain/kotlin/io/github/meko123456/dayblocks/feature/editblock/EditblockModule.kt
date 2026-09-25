package io.github.meko123456.dayblocks.feature.editblock

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Opened with an [EditBlockArgs] via `parametersOf`, since each editor is for a particular block or gap. */
val editblockModule = module {
    viewModel { (args: EditBlockArgs) -> EditBlockViewModel(args, get(), get(), get(), get(), get()) }
}
