plugins {
    id("dayblocks.kmp.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The buddy engine: pure Kotlin, like the domain. Today shows the buddy's face and
            // words, and they are decided there, not here.
            implementation(project(":core:buddy"))
        }
    }
}
