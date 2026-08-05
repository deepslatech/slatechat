import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.10"
    id("com.gradleup.shadow") version "9.6.1"
}

// group/version come from gradle.properties

// ================= REPOSITORIES =================
repositories {
    mavenCentral()

    // Official PaperMC repository
    maven("https://repo.papermc.io/repository/maven-public/")

    // PlaceholderAPI repository
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")

    // Scarsz Nexus (DiscordSRV)
    maven("https://nexus.scarsz.me/content/groups/public/")
}

// ================= DEPENDENCIES =================
dependencies {
    // Paper API (official server API, no NMS required) - provided by the server at runtime
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Kotlin standard library (needed at runtime, gets shaded into the final jar)
    implementation(kotlin("stdlib"))

    // PlaceholderAPI (provided by the server if installed)
    compileOnly("me.clip:placeholderapi:2.11.6")

    // DiscordSRV (provided by the server)
    compileOnly("com.discordsrv:discordsrv:1.28.0")
}

// ================= BUILD CONFIGURATION =================

// Java 21 to match Paper's target - the Kotlin compiler follows the same toolchain
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// Resource filtering for plugin.yml (${project.name} / ${project.version} placeholders,
// same ${...} syntax Maven resource filtering used, so plugin.yml didn't need to change)
tasks.processResources {
    // plugin.yml references ${project.name} / ${project.version}, Maven-style
    filesMatching("plugin.yml") {
        expand(mapOf("project" to mapOf("name" to project.name, "version" to project.version)))
    }
}

// Shade the Kotlin runtime (and any other implementation deps) into the final jar.
// The plain `jar` task is disabled so only the shaded jar is produced, avoiding
// two tasks fighting over the same build/libs/SlateChat-<version>.jar output.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
