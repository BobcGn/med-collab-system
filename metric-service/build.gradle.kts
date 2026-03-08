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
