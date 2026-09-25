// Test support only: in-memory repositories honouring the domain interfaces' contracts, shared by
// every feature's ViewModel tests. No production module depends on this — the feature convention
// plugin adds it to commonTest and nowhere else — so it cannot leak into the app.
plugins {
    id("dayblocks.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))
            api(project(":core:common"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
