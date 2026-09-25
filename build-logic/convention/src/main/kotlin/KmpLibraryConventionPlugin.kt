import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The base every DayBlocks library module applies: a Kotlin Multiplatform library targeting
 * Android, iosArm64 and iosSimulatorArm64 — the three the app actually ships.
 *
 * Android is configured through `com.android.kotlin.multiplatform.library`, AGP 9's own KMP
 * plugin, rather than `com.android.library`. AGP 9 refuses the old pairing outright and offers
 * `android.builtInKotlin=false` / `android.newDsl=false` only as a *temporary* bypass — a new
 * project should not start on a flag its toolchain has already scheduled for removal.
 *
 * Every module is multiplatform rather than JVM-only on purpose. The domain and the buddy engine
 * have to be reachable from the iOS framework, and a JVM-only module would quietly exclude iOS
 * while still looking correct from Android.
 *
 * No iosX64: that is the Intel-Mac simulator, and neither this machine nor the macOS CI runner is
 * Intel, so it would be a target nobody ever builds.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.kotlin.multiplatform.library")

        extensions.configure(KotlinMultiplatformExtension::class.java) {
            (this as ExtensionAware).extensions.configure(KotlinMultiplatformAndroidLibraryExtension::class.java) {
                namespace = namespaceFor(path)
                // 37 because AndroidX requires it: Compose BOM 2026.09.00 and core-ktx 1.19.0
                // publish AAR metadata declaring a minimum compileSdk of 37, and a module on 36
                // fails at checkAarMetadata before compiling a line.
                compileSdk = 37
                minSdk = 26
                // commonTest also runs on the JVM as testAndroidHostTest: the fast loop, seconds
                // rather than the minute a simulator run costs. iosSimulatorArm64Test still runs
                // the same tests on Native, which is the half that catches platform divergence.
                withHostTestBuilder {}
            }
            iosArm64()
            iosSimulatorArm64()
        }

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("commonTestImplementation", libs.findLibrary("kotlin-test").get())
        }
    }

    /**
     * `:core:domain` → `io.github.meko123456.dayblocks.core.domain`.
     *
     * Derived rather than declared in fourteen build files, so the namespace always matches the
     * module and the source packages written against it — a copy-pasted namespace is exactly the
     * kind of mistake that compiles and then collides two R classes at merge time.
     */
    private fun namespaceFor(path: String): String =
        "io.github.meko123456.dayblocks" + path.replace(':', '.').lowercase()
}
