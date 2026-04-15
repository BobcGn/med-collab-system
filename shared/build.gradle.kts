import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
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

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)

    implementation(libs.bundles.exposed.legacy.h2)
    implementation(libs.bundles.ktor.shared.security)

    // Testing
    testImplementation(libs.kotlin.test.junit)
}
