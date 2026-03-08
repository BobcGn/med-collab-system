plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

application {
    mainClass = "com.example.ApplicationKt"
}

tasks.register("prepareKotlinBuildScriptModel")

dependencies {
    // Ktor Core
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.cors.jvm)
    implementation(libs.ktor.server.content.negotiation.jvm)
    implementation(libs.ktor.serialization.kotlinx.json.jvm)

    // Ktor Server Auth (JWT)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.auth)

    // Ktor Server Config
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.host.common.jvm)
    implementation(libs.ktor.server.status.pages.jvm)

    // Shared Module
    implementation(project(":shared"))

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.java.time)
    implementation(libs.mysql.connector.j.v84)

    // Logging
    implementation(libs.logback.classic)

    // Testing
    testImplementation(libs.ktor.server.test.host.jvm)
    testImplementation(libs.kotlin.test.junit)
}
