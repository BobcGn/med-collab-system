plugins {
    kotlin("jvm") version "2.2.21"
    id("io.ktor.plugin") version "3.3.2"
    kotlin("plugin.serialization") version "2.2.21"
}

application {
    mainClass = "com.example.ApplicationKt"
}
tasks.register("prepareKotlinBuildScriptModel")
dependencies {
    // Ktor Server Core
    implementation("io.ktor:ktor-server-core-jvm:3.3.2")
    implementation("io.ktor:ktor-server-netty-jvm:3.3.2")
    implementation("io.ktor:ktor-server-cors-jvm:3.3.2")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.3.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.3.2")

    // Ktor Server Auth (JWT)
    implementation("io.ktor:ktor-server-auth-jwt:3.3.2")
    implementation("io.ktor:ktor-server-auth:3.3.2")

    // Ktor Server Metrics
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:3.3.2")
    implementation("io.micrometer:micrometer-registry-prometheus:1.13.6")

    // Ktor Client (用于请求转发)
    implementation("io.ktor:ktor-client-core-jvm:3.3.2")
    implementation("io.ktor:ktor-client-cio-jvm:3.3.2")

    // Shared Module
    implementation(project(":shared"))

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.3.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.21")
}
