import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
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
    // Kotlin
    implementation(libs.kotlin.stdlib)

    implementation(libs.bundles.exposed.legacy.h2)
    implementation(libs.bundles.ktor.shared.security)

    // Testing
    testImplementation(libs.kotlin.test.junit)
}
