allprojects {
    group = "com.example.medcollab"
    version = "0.0.1"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "medcollab.kotlin-conventions")
}
