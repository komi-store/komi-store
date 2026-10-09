plugins {
    alias(libs.plugins.convention.kmp.library)
    alias(libs.plugins.convention.room)
    alias(libs.plugins.convention.buildkonfig)
}

android {
    buildFeatures {
        aidl = true
    }
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.datetime)

                implementation(projects.core.domain)

                implementation(libs.bundles.ktor.common)
                implementation(libs.bundles.koin.common)

                implementation(libs.touchlab.kermit)

                implementation(libs.ksafe)

                implementation(libs.datastore)
                implementation(libs.datastore.preferences)
            }
        }

        androidMain {
            dependencies {
                implementation(libs.shizuku.api)
                implementation(libs.shizuku.provider)
                compileOnly(libs.hidden.api.stub)

                implementation(libs.dhizuku.api)

                implementation(libs.libsu.core)

                implementation(libs.ktor.client.okhttp)

                implementation(libs.androidx.work.runtime)
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
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
