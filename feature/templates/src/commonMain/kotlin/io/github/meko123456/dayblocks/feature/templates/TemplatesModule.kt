package io.github.meko123456.dayblocks.feature.templates

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val templatesModule = module {
    viewModel { TemplatesViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
