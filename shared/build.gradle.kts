plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.21")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.55.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.55.0")
    implementation("com.h2database:h2:2.3.232")

    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.3.2")

    // Ktor Config
    implementation("io.ktor:ktor-server-config-yaml:3.3.2")
    implementation("io.ktor:ktor-server-core:3.3.2")
    implementation("io.ktor:ktor-server-auth:3.3.2")
    implementation("io.ktor:ktor-server-auth-jwt:3.3.2")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.21")
}

