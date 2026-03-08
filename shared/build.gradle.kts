plugins {
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Kotlin
    implementation(libs.kotlin.stdlib)

    implementation(libs.bundles.exposed.legacy.h2)
    implementation(libs.bundles.ktor.shared.security)

    // Testing
    testImplementation(libs.kotlin.test.junit)
}
