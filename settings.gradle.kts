plugins {
    // Lets Gradle auto-provision a JDK 21 toolchain if one isn't already installed locally
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "SlateChat"
