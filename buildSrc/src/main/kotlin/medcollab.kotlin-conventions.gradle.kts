import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm")
}

extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(21)
}
