// The buddy's message engine: which line it says, when, and whether it is allowed to say anything
// at all. Pure Kotlin and no Compose — the mood a screen draws is chosen here, the drawing is
// :core:designsystem's job. Fully unit tested; it is the feature the app lives or dies by.
plugins {
    id("dayblocks.kmp.library")
    id("dayblocks.kmp.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            implementation(project(":core:common"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
