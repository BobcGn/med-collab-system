pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "med-collab-system"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":auth-service", ":shared", ":patient-service", ":metric-service",":api-gateway")
