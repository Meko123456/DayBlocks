// The domain layer. Pure Kotlin: no Compose, no Android, no SQLDelight, no Koin-Android — the
// only dependencies are kotlinx-datetime and coroutines, which are language-level libraries
// rather than frameworks. Nothing here knows how a block is stored or drawn.
//
// It applies dayblocks.kmp.library (not kmp.feature) precisely so the Compose dependency is not
// on its path: an accidental `import androidx.compose.*` in a use case will not compile.
plugins {
    id("dayblocks.kmp.library")
    id("dayblocks.kmp.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:common"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
