plugins {
    id("dayblocks.kmp.library")
    id("dayblocks.kmp.koin")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:common"))
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies { implementation(libs.sqldelight.driver.android) }
        iosMain.dependencies { implementation(libs.sqldelight.driver.native) }
    }
}

sqldelight {
    databases {
        create("DayBlocksDatabase") {
            packageName.set("io.github.meko123456.dayblocks.database")
        }
    }
}
