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
            // The schema each released version had, as a .db per version. verifyMigrations applies
            // every .sqm to the version before it and checks the result is what the .sq files now
            // describe, so a column added to a .sq without a migration fails the build instead of
            // only upgraded phones, which a fresh install never shows.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
