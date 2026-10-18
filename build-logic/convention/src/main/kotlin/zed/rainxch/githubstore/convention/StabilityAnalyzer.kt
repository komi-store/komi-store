package zed.rainxch.githubstore.convention

import com.skydoves.compose.stability.gradle.StabilityAnalyzerExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

internal fun Project.configureStabilityAnalyzer() {
    pluginManager.apply("com.github.skydoves.compose.stability.analyzer")
    extensions.configure<StabilityAnalyzerExtension> {
        stabilityValidation {
            ignoreNonRegressiveChanges.set(true)
        }
    }
}
