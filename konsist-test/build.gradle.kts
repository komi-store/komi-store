plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    testImplementation(libs.konsist)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    outputs.upToDateWhen { false }
}
