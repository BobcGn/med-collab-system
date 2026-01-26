plugins {
    kotlin("jvm") version "2.2.21" apply false
    id("io.ktor.plugin") version "3.3.2" apply false
    kotlin("plugin.serialization") version "2.2.21" apply false
}

allprojects {
    group = "com.example.medcollab"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }
}
