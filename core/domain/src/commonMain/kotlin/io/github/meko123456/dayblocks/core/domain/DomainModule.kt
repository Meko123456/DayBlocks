package io.github.meko123456.dayblocks.core.domain

import io.github.meko123456.dayblocks.core.domain.time.PlanningDayRule
import io.github.meko123456.dayblocks.core.domain.usecase.AutoFillDay
import io.github.meko123456.dayblocks.core.domain.usecase.CopyDay
import io.github.meko123456.dayblocks.core.domain.usecase.DetectOverlaps
import io.github.meko123456.dayblocks.core.domain.usecase.FindFreeTime
import io.github.meko123456.dayblocks.core.domain.usecase.GenerateDayFromTemplate
import io.github.meko123456.dayblocks.core.domain.usecase.IdGenerator
import io.github.meko123456.dayblocks.core.domain.usecase.RandomIdGenerator
import io.github.meko123456.dayblocks.core.domain.usecase.ResolveNow
import io.github.meko123456.dayblocks.core.domain.usecase.SaveDayAsTemplate
import io.github.meko123456.dayblocks.core.domain.usecase.ScoreAdherence
import org.koin.dsl.module

/**
 * Use cases are stateless, so they are factories rather than singletons: nothing is shared, and
 * a test that builds one gets exactly what production gets.
 *
 * The repository interfaces are declared in this module but bound in :core:data's module, which
 * is the whole point of the split — the domain names what it needs, the data layer supplies it.
 */
val domainModule = module {
    single<IdGenerator> { RandomIdGenerator }
    single { PlanningDayRule() }
    factory { DetectOverlaps() }
    factory { GenerateDayFromTemplate(get()) }
    factory { ScoreAdherence() }
    factory { ResolveNow() }
    factory { FindFreeTime() }
    factory { SaveDayAsTemplate(get()) }
    factory { CopyDay(get()) }
    factory { AutoFillDay(get(), get(), get(), get()) }
}
