plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

application {
    mainClass = "com.example.ApplicationKt"
}
tasks.register("prepareKotlinBuildScriptModel")
dependencies {
    // Ktor Server Core
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.server.cors.jvm)
    implementation(libs.ktor.server.content.negotiation.jvm)
    implementation(libs.ktor.serialization.kotlinx.json.jvm)

    // Ktor Server Auth (JWT)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.auth)

    // Ktor Server Metrics
    implementation(libs.ktor.server.metrics.micrometer.jvm)
    implementation(libs.micrometer.registry.prometheus)

    // Ktor Client (用于请求转发)
    implementation(libs.ktor.client.core.jvm)
    implementation(libs.ktor.client.cio.jvm)

    // Shared Module
    implementation(project(":shared"))

    // Logging
    implementation(libs.logback.classic)

    // Testing
    testImplementation(libs.ktor.server.test.host.jvm)
    testImplementation(libs.kotlin.test.junit)
}
