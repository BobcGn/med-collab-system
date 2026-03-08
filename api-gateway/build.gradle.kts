plugins {
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

application {
    mainClass = "com.example.ApplicationKt"
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
