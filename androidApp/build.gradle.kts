plugins {
    alias(libs.plugins.convention.android.application.compose)
}

android {
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.data)
    implementation(projects.core.domain)
    implementation(projects.core.presentation)

    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)

    implementation(libs.core.splashscreen)

    implementation(libs.koin.android)
    implementation(libs.touchlab.kermit)
}

tasks.register("printRuntimeDependencies") {
    description = "Lists all runtime dependencies for licenses.json maintenance."
    group = "verification"
    val coordinatesProvider: Provider<List<String>> =
        project.provider {
            val cfg =
                configurations.findByName("releaseRuntimeClasspath")
                    ?: configurations.findByName("androidReleaseRuntimeClasspath")
                    ?: error("No release runtime classpath configuration found.")
            cfg.incoming.resolutionResult.allComponents
                .asSequence()
                .map { it.id }
                .filterIsInstance<ModuleComponentIdentifier>()
                .map { "${it.group}:${it.module}:${it.version}" }
                .distinct()
                .sorted()
                .toList()
        }
    notCompatibleWithConfigurationCache("Resolves Android variant configurations at execution time.")
    doLast {
        coordinatesProvider.get().forEach { println(it) }
    }
}

dependencies {
    androidTestImplementation(projects.core.presentation)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
