package io.github.meko123456.dayblocks.feature.onboarding

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val onboardingModule = module {
    viewModel { OnboardingViewModel(get(), get(), get(), get(), get(), get(), get()) }
}
