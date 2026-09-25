import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.compose.ComposeExtension

/**
 * Adds Compose Multiplatform to a module that already applies `dayblocks.kmp.library`.
 *
 * Kept separate from the library plugin because three modules deliberately have no UI at all —
 * :core:domain, :core:buddy and :core:common are pure Kotlin, and the only thing stopping a
 * Compose import drifting into the domain layer is that the dependency is not on its path.
 */
class KmpComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        val compose = extensions.getByType(ComposeExtension::class.java).dependencies
        dependencies {
            add("commonMainImplementation", compose.runtime)
            add("commonMainImplementation", compose.foundation)
            add("commonMainImplementation", compose.ui)
            add("commonMainImplementation", libs.findLibrary("compose-material3").get())
        }
    }
}
