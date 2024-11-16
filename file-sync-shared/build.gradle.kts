plugins {
    alias(libs.plugins.kotlin.jvm)

    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.serialization.kotlinx.json)
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            // Use Kotlin Test test framework
            useKotlinTest("2.0.0")
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
