import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("dayblocks.kmp.library")
    id("dayblocks.kmp.koin")
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
            implementation(project(":core:database"))
            implementation(project(":core:common"))
            implementation(libs.sqldelight.coroutines)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.coroutines)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
            // MapSettings: the settings store's tests run against an in-memory ObservableSettings.
            implementation(libs.multiplatform.settings.test)
        }
        // The repositories' tests run against real SQLite on both platforms: sqlite-jdbc on the
        // JVM, and on iOS the system SQLite through :core:database's own DriverFactory.
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.driver.sqlite)
        }
    }

    // The test executable calls the system SQLite through :core:database, but nothing links it:
    // SQLDelight's Gradle plugin adds -lsqlite3 only to binaries of the module that applies it, and
    // neither SQLiter's klib nor the native driver's asks for it on iOS. The same gap is why
    // iosApp's project.yml passes -lsqlite3 to the app.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.configureEach { linkerOpts("-lsqlite3") }
    }
}
