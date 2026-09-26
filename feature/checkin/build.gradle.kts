plugins {
    id("dayblocks.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The buddy's reaction to the day's score is decided by the engine, like Today's words.
            implementation(project(":core:buddy"))
        }
    }
}
