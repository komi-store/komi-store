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
                api(libs.ktor.client.core)

                implementation(libs.jetbrains.lifecycle.compose)

                implementation(libs.jetbrains.compose.components.resources)
                implementation(libs.androidx.compose.ui.tooling.preview)

                api(libs.markdown.renderer)
                implementation(libs.markdown.renderer.coil3)
                implementation(libs.highlights)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.ktor.client.okhttp)
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
