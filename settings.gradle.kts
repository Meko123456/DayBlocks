pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DayBlocks"

// The two app shells. :composeApp is a library holding the shared entry point, navigation graph
// and Koin startup; :androidApp is the Android application that hosts it, plus the widget and
// notification receivers that can only exist on that platform. iosApp is an Xcode project rather
// than a Gradle module and links :composeApp's framework.
include(":composeApp")
include(":androidApp")

include(":core:common")
include(":core:domain")
include(":core:data")
include(":core:database")
include(":core:notifications")
include(":core:buddy")
include(":core:designsystem")

// Feature modules may depend on core modules but never on each other; navigation between them is
// wired in :composeApp. Enforced by the dayblocks.kmp.feature convention plugin, which grants a
// feature its core dependencies and nothing else.
include(":feature:onboarding")
include(":feature:today")
include(":feature:editblock")
include(":feature:templates")
include(":feature:checkin")
include(":feature:stats")
include(":feature:settings")
