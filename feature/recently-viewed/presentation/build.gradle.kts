plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)

                implementation(projects.core.domain)
                implementation(projects.core.presentation)

                implementation(libs.kotlinx.collections.immutable)

                implementation(libs.androidx.compose.ui.tooling.preview)
                implementation(libs.jetbrains.compose.components.resources)
            }
        }
    }
}
