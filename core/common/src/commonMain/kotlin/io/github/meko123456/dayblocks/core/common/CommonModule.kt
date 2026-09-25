package io.github.meko123456.dayblocks.core.common

import org.koin.dsl.module

/**
 * One Koin module per Gradle module. This one supplies the two things every other module needs and
 * neither of which should ever be reached statically: the clock and the dispatchers.
 */
val commonModule = module {
    single<TimeProvider> { SystemTimeProvider() }
    single<AppDispatchers> { DefaultAppDispatchers }
}
