import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import zed.rainxch.githubstore.convention.configureKotlinAndroid
import zed.rainxch.githubstore.convention.pathToPackageName

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.library")
            }

            extensions.configure<LibraryExtension> {
                namespace = this@with.pathToPackageName()

                configureKotlinAndroid(this)
            }
        }
    }
}
