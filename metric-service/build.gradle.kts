import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaLanguageVersion
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
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.withType<JavaExec>().configureEach {
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(libs.bundles.ktor.server.base.jvm)
    implementation(libs.bundles.ktor.server.auth)
    implementation(libs.bundles.ktor.server.config.jvm)

    // Shared Module
    implementation(project(":shared"))

    implementation(libs.bundles.exposed.main)
    implementation(libs.mysql.connector.j.v91)

    // Logging
    implementation(libs.logback.classic)

    // Koog
    implementation(libs.koog.ktor)

    testImplementation(libs.bundles.test.kotlin.ktor.server)
    implementation(libs.kotlin.test)
}
