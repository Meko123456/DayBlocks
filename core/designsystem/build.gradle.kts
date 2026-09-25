plugins {
    id("dayblocks.kmp.library")
    id("dayblocks.kmp.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:domain"))
        }
    }
}
