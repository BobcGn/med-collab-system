plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
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

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    implementation("io.jsonwebtoken:jjwt-impl:0.11.5")
    implementation("io.jsonwebtoken:jjwt-jackson:0.11.5")

    // Ktor Config
    implementation("io.ktor:ktor-server-config-yaml:3.3.2")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.21")
}
