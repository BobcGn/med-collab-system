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
    implementation(libs.bundles.ktor.server.config.jvm)

    // Shared Module
    implementation(project(":shared"))

    implementation(libs.bundles.exposed.main)
    implementation(libs.mysql.connector.j.v91)
    runtimeOnly(libs.h2)

    // Logging
    implementation(libs.logback.classic)

    // Koog
    implementation(libs.koog.ktor)

    testImplementation(libs.bundles.test.kotlin.ktor.server)
    implementation(libs.kotlin.test)
}
