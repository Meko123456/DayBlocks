plugins {
    `kotlin-dsl`
}

group = "io.github.meko123456.dayblocks.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "dayblocks.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpCompose") {
            id = "dayblocks.kmp.compose"
            implementationClass = "KmpComposeConventionPlugin"
        }
        register("kmpKoin") {
            id = "dayblocks.kmp.koin"
            implementationClass = "KmpKoinConventionPlugin"
        }
        register("kmpFeature") {
            id = "dayblocks.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
    }
}
