plugins {
    kotlin("jvm") version "2.2.21"
    id("io.ktor.plugin") version "3.3.2"
    kotlin("plugin.serialization") version "2.2.21"
}

application {
    mainClass = "com.example.medcollab.auth.ApplicationKt"
}

tasks.register("prepareKotlinBuildScriptModel")

dependencies {
    // Ktor Core
    implementation("io.ktor:ktor-server-core-jvm:3.3.2")
    implementation("io.ktor:ktor-server-netty-jvm:3.3.2")
    implementation("io.ktor:ktor-server-cors-jvm:3.3.2")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.3.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.3.2")

    // Ktor Auth
    implementation("io.ktor:ktor-server-auth-jvm:3.3.2")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:3.3.2")

    // Shared Module
    implementation(project(":shared"))

    // Database
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.61.0")
    implementation("org.jetbrains.exposed:exposed-java-time:0.61.0")
    implementation("com.mysql:mysql-connector-j:8.4.0")


    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.ktor:ktor-server-core:3.3.2")
    implementation("io.ktor:ktor-server-host-common:3.3.2")
    implementation("io.ktor:ktor-server-status-pages:3.3.2")
    implementation("io.ktor:ktor-server-core:3.3.2")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.3.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.21")
}
