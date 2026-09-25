import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Koin for a module that publishes its own DI module.
 *
 * One Koin module per Gradle module is the rule here, so wiring is discoverable: if a class lives
 * in `:core:data`, its binding lives in `:core:data`'s `dataModule`. :composeApp is then the only
 * place that knows the full list, and it is the only place that starts Koin.
 */
class KmpKoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("commonMainImplementation", libs.findLibrary("koin-core").get())
            add("commonTestImplementation", libs.findLibrary("koin-test").get())
        }
    }
}
