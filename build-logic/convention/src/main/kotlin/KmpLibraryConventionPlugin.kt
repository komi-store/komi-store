import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import zed.rainxch.githubstore.convention.configureKotlin
import zed.rainxch.githubstore.convention.configureKotlinMultiplatform
import zed.rainxch.githubstore.convention.libs

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("com.android.kotlin.multiplatform.library")
                // The Android KMP plugin creates its lint tasks only when this plugin is applied too
                apply("com.android.lint")
                apply("org.jetbrains.kotlin.plugin.serialization")
                apply("zed.rainxch.convention.quality")
            }

            configureKotlinMultiplatform()
            configureKotlin()

            dependencies {
                "commonMainImplementation"(libs.findLibrary("kotlinx-serialization-json").get())
            }
        }
    }
}
