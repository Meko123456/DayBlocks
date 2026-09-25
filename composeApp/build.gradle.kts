// The shared application shell: root composable, navigation graph and Koin startup. It is a
// library rather than the Android application so both platforms consume the same entry point —
// :androidApp hosts it in an Activity, iosApp links the ComposeApp framework it produces.
plugins {
    id("dayblocks.kmp.library")
    id("dayblocks.kmp.compose")
    id("dayblocks.kmp.koin")
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            implementation(project(":core:domain"))
            implementation(project(":core:data"))
            implementation(project(":core:database"))
            implementation(project(":core:notifications"))
            implementation(project(":core:buddy"))
            implementation(project(":core:designsystem"))

            implementation(project(":feature:onboarding"))
            implementation(project(":feature:today"))
            implementation(project(":feature:editblock"))
            implementation(project(":feature:templates"))
            implementation(project(":feature:checkin"))
            implementation(project(":feature:stats"))
            implementation(project(":feature:settings"))

            implementation(libs.jb.navigation.compose)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}
