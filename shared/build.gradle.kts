plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.register("prepareKotlinBuildScriptModel")

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)

    // Database
    implementation(libs.exposed.core.legacy)
    implementation(libs.exposed.jdbc.legacy)
    implementation(libs.h2)

    // Serialization
    implementation(libs.ktor.serialization.kotlinx.json.jvm)

    // Ktor Config
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)

    // Testing
    testImplementation(libs.kotlin.test.junit)
}
