plugins {
    alias(libs.plugins.flatpak.gradle.generator)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.hot.reload) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.stability.analyzer) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kover)
}

tasks.flatpakGradleGenerator {
    outputFile = file("packaging/flatpak/flatpak-sources.json")
    downloadDirectory.set("./offline-repository")
    excludeConfigurations.set(listOf("testCompileClasspath", "testRuntimeClasspath"))
}

spotless {
    ratchetFrom("origin/main")
    // Excludes sit on the tree so the walk skips them; targetExclude would subtract them only after walking
    // every worktree under .claude/.
    kotlin {
        target(
            fileTree(rootDir) {
                include("**/*.kt")
                exclude(".git/**", ".gradle/**", "**/build/**", "**/generated/**", ".claude/**")
            },
        )
        ktlint(libs.versions.ktlint.get())
            .setEditorConfigPath("$rootDir/.editorconfig")
            .customRuleSets(listOf("io.nlopez.compose.rules:ktlint:${libs.versions.compose.rules.get()}"))
        file("config/ktlint/baseline.txt").readLines().filter { it.isNotBlank() }.forEach { line ->
            val (rule, filePath) = line.trim().split(" ", limit = 2)
            suppressLintsFor {
                step = "ktlint"
                shortCode = rule
                path = filePath
            }
        }
    }
    kotlinGradle {
        target(
            fileTree(rootDir) {
                include("**/*.gradle.kts")
                exclude(".git/**", ".gradle/**", "**/build/**", ".claude/**")
            },
        )
        ktlint(libs.versions.ktlint.get()).setEditorConfigPath("$rootDir/.editorconfig")
    }
}

dependencies {
    // :core:data-aidl has no Kotlin and no Kover plugin
    subprojects
        .filter { it.buildFile.exists() && it.path !in setOf(":konsist-test", ":core:data-aidl") }
        .forEach { kover(project(it.path)) }
}

kover {
    reports {
        filters {
            excludes {
                classes("*.BuildKonfig", "*_Impl", "*_Impl\$*", "*.generated.resources.*")
            }
        }
    }
}
