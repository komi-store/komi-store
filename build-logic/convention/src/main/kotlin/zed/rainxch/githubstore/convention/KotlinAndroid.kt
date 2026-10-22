package zed.rainxch.githubstore.convention

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    with(commonExtension) {
        compileSdk =
            libs
                .findVersion("projectCompileSdkVersion")
                .get()
                .toString()
                .toInt()

        defaultConfig.minSdk =
            libs
                .findVersion("projectMinSdkVersion")
                .get()
                .toString()
                .toInt()

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        compileOptions.isCoreLibraryDesugaringEnabled = true

        configureLint(lint)

        configureKotlin()

        dependencies {
            "coreLibraryDesugaring"(libs.findLibrary("android-desugarJdkLibs").get())
        }
    }
}

internal fun Project.configureLint(lint: Lint) {
    lint.baseline = file("lint-baseline.xml")
    lint.disable.addAll(setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion"))
    lint.abortOnError = true
    lint.checkReleaseBuilds = false
    lint.htmlReport = true
    lint.xmlReport = true
    lint.sarifReport = true
}

internal fun Project.configureKotlin() {
    val warningsAsErrors = findProperty("komi.warningsAsErrors")?.toString()?.toBoolean() ?: true
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(warningsAsErrors)
        }
    }
}
