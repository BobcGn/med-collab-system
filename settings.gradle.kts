rootProject.name = "med-collab-system"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":auth-service", ":report-service", ":shared", ":patient-service", ":metric-service",":api-gateway")
