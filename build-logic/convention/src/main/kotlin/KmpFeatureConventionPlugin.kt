import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project

/**
 * A feature module: a Compose Multiplatform screen with an MVI ViewModel, wired to the domain.
 *
 * A new feature's build file is then `plugins { id("dayblocks.kmp.feature") }` plus a namespace.
 *
 * The dependency set is the architectural boundary, not a convenience:
 *
 *  - `:core:domain` and never `:core:data`. A screen talks to repository interfaces and use cases,
 *    so it cannot reach SQLDelight, the notification scheduler or any platform API even by
 *    accident — the types are not on its compile path.
 *  - No feature depends on another feature. Navigation between screens is wired in :composeApp,
 *    which is the only module that knows the graph. Nothing in this plugin grants a feature
 *    dependency, so an import of one feature from another does not compile.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dayblocks.kmp.library")
        pluginManager.apply("dayblocks.kmp.compose")
        pluginManager.apply("dayblocks.kmp.koin")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("commonMainImplementation", project(":core:domain"))
            add("commonMainImplementation", project(":core:designsystem"))
            add("commonMainImplementation", project(":core:common"))

            add("commonMainImplementation", libs.findLibrary("jb-lifecycle-viewmodel-compose").get())
            add("commonMainImplementation", libs.findLibrary("jb-lifecycle-runtime-compose").get())
            add("commonMainImplementation", libs.findLibrary("koin-compose-viewmodel").get())
            add("commonMainImplementation", libs.findLibrary("kotlinx-coroutines-core").get())
            add("commonMainImplementation", libs.findLibrary("kotlinx-datetime").get())

            // Every feature is an MVI screen, so every feature's tests need a scheduler they
            // control and a way to assert on a Flow of states.
            add("commonTestImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            add("commonTestImplementation", libs.findLibrary("turbine").get())
            // Shared fakes of the domain's repositories, so seven screens' tests do not each keep
            // their own copy that can drift from the interface contract.
            add("commonTestImplementation", project(":core:testing"))

            // Screenshot tests: each screen's stateless content, rendered by Robolectric's native
            // graphics on the JVM and compared with a committed PNG by Roborazzi.
            for (lib in listOf("robolectric", "roborazzi", "roborazzi-compose", "androidx-test-junit", "compose-ui-test-junit4", "compose-ui-test-manifest")) {
                add("androidHostTestImplementation", libs.findLibrary(lib).get())
            }
        }

        // Verifying is the default, so testAndroidHostTest — which CI already runs — fails on any
        // screen that no longer looks like its PNG. `-Proborazzi.record=true` rewrites the PNGs
        // instead; review the diff before committing it. Roborazzi's Gradle plugin would set the
        // same two properties, but it does not know AGP 9's KMP library plugin, and without one
        // of them captureRoboImage silently does nothing.
        val record = providers.gradleProperty("roborazzi.record").map { it.toBoolean() }.orElse(false)
        tasks.withType(Test::class.java).configureEach {
            if (name == "testAndroidHostTest") {
                systemProperty("roborazzi.test.record", record.get())
                systemProperty("roborazzi.test.verify", !record.get())
                // Text and shapes drawn the same way on a Mac and on the Linux CI runner.
                systemProperty("robolectric.graphicsMode", "NATIVE")
                systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                // Robolectric sets up file descriptors through jdk.internal.access, which JDK 21
                // does not export; without this every screenshot test dies in setup with
                // "Failed to interact with raw FileDescriptor internals; perhaps JRE has changed?".
                jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
}
