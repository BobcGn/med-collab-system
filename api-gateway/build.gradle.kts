import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("io.ktor.plugin")
    id("org.jetbrains.kotlin.plugin.serialization")
}

application {
    mainClass = "com.example.ApplicationKt"
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_24)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(24)
}

dependencies {
    implementation(libs.bundles.ktor.server.base.jvm)
    implementation(libs.bundles.ktor.server.auth)

    // Ktor Server Metrics
    implementation(libs.ktor.server.metrics.micrometer.jvm)
    implementation(libs.micrometer.registry.prometheus)

    implementation(libs.bundles.ktor.client.base.jvm)

    // Shared Module
    implementation(project(":shared"))

    // Logging
    implementation(libs.logback.classic)

    testImplementation(libs.bundles.test.kotlin.ktor.server)
}
