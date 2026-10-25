rootProject.name = "GithubStore"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jitpack.io") {
            mavenContent {
                includeGroupAndSubgroups("com.github.topjohnwu")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":androidApp")
include(":composeApp")
include(":core:domain")
include(":core:data")
include(":core:data-aidl")
include(":core:presentation")
include(":feature:apps:data")
include(":feature:apps:domain")
include(":feature:apps:presentation")
include(":feature:auth:domain")
include(":feature:auth:data")
include(":feature:auth:presentation")
include(":feature:details:domain")
include(":feature:details:data")
include(":feature:details:presentation")
include(":feature:dev-profile:presentation")
include(":feature:dev-profile:data")
include(":feature:dev-profile:domain")
include(":feature:repo-pages:domain")
include(":feature:repo-pages:data")
include(":feature:repo-pages:presentation")
include(":feature:reviews:presentation")
include(":feature:favourites:data")
include(":feature:favourites:domain")
include(":feature:favourites:presentation")
include(":feature:recently-viewed:presentation")
include(":feature:tweaks:presentation")
include(":feature:home:domain")
include(":feature:home:data")
include(":feature:home:presentation")
include(":feature:feed:domain")
include(":feature:feed:data")
include(":feature:feed:presentation")
include(":feature:starred:domain")
include(":feature:starred:data")
include(":feature:starred:presentation")
include(":feature:search:domain")
include(":feature:search:data")
include(":feature:search:presentation")
include(":feature:profile:presentation")
include(":konsist-test")
