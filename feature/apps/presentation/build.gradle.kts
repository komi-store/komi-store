plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.collections.immutable)
                implementation(libs.kotlinx.datetime)

                implementation(projects.core.domain)
                implementation(projects.core.presentation)
                implementation(projects.feature.apps.domain)

                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(libs.jetbrains.compose.components.resources)
            }
        }
    }
}
