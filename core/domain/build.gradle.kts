plugins {
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        jvmTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
