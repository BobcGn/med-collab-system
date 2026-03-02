rootProject.name = "med-collab-system"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

include(":auth-service", ":shared", ":patient-service", ":metric-service",":api-gateway")
