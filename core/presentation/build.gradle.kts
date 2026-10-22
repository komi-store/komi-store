plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.collections.immutable)

                implementation(projects.core.domain)

                implementation(libs.coil3.compose)
                implementation(libs.coil3.network.ktor)
                implementation(libs.coil3.svg)

                implementation(libs.jetbrains.lifecycle.compose)

                implementation(libs.jetbrains.compose.components.resources)
                implementation(libs.androidx.compose.ui.tooling.preview)

                implementation(libs.markdown.renderer)
                implementation(libs.markdown.renderer.coil3)
                implementation(libs.highlights)
                implementation(libs.material.kolor)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.androidx.core.ktx)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "zed.rainxch.githubstore.core.presentation.res"
    generateResClass = auto
}
