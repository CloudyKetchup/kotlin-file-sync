plugins {
    alias(libs.plugins.kotlin.jvm)

    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.serialization.kotlinx.json)
}