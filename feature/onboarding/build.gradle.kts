plugins {
    id("dayblocks.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The tone page lets each tone speak for itself, in the buddy engine's own words.
            implementation(project(":core:buddy"))
        }
    }
}
