plugins {
    alias(libs.plugins.convention.kmp.library)
    alias(libs.plugins.convention.buildkonfig)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)

                implementation(projects.core.domain)
                implementation(projects.core.data)
                implementation(projects.feature.devProfile.domain)

                implementation(libs.bundles.ktor.common)
                implementation(libs.bundles.koin.common)
            }
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
