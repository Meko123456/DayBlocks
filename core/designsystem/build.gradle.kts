plugins {
    id("dayblocks.kmp.library")
    id("dayblocks.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            // api: every feature's ViewModel extends MviViewModel, so the ViewModel type has to be
            // visible to them through this module.
            api(libs.jb.lifecycle.viewmodel)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
