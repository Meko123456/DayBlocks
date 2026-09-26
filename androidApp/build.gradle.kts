// The Android application. Deliberately not a Kotlin Multiplatform module: under AGP 9 an app
// module compiles its own Kotlin (built-in Kotlin), and everything shared already lives in
// :composeApp. What stays here is only what can exist on Android alone — the Activity, the
// Application that starts Koin, the notification receivers, and (in a later step) the Glance
// widget.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "io.github.meko123456.dayblocks"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.meko123456.dayblocks"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // Lint every module the app depends on. The fourteen library modules have no lint task of
        // their own in CI, so without this a warning in :feature:today would be reported nowhere.
        checkDependencies = true
        warningsAsErrors = true
        // Dependabot owns version bumps and opens a PR per release; lint repeating "a newer
        // version is available" would turn CI red on every upstream publish and bury real findings.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(project(":composeApp"))
    implementation(project(":core:buddy"))
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:notifications"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)
}
