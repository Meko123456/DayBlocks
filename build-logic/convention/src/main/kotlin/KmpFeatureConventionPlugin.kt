import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
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
        }
    }
}
