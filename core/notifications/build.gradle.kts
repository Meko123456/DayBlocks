// The scheduling port plus its two platform implementations. The interface lives in commonMain so
// the domain can schedule without knowing whether an AlarmManager or a UNUserNotificationCenter
// is on the other side.
plugins {
    id("dayblocks.kmp.library")
    id("dayblocks.kmp.koin")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:common"))
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
