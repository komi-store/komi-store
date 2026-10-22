package zed.rainxch.githubstore.convention

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

internal fun Project.configureAndroidTarget() {
    extensions.configure<KotlinMultiplatformExtension> {
        (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
            namespace = this@configureAndroidTarget.pathToPackageName()
            compileSdk =
                libs
                    .findVersion("projectCompileSdkVersion")
                    .get()
                    .toString()
                    .toInt()
            minSdk =
                libs
                    .findVersion("projectMinSdkVersion")
                    .get()
                    .toString()
                    .toInt()
            enableCoreLibraryDesugaring = true

            androidResources {
                enable = true
                resourcePrefix = this@configureAndroidTarget.pathToResourcePrefix()
            }

            configureLint(lint)

            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    dependencies {
        "coreLibraryDesugaring"(libs.findLibrary("android-desugarJdkLibs").get())
    }
}
