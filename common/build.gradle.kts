import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

// ================= DEPENDENCIES =================
dependencies {
    // Kotlin standard library (needed at runtime -- paper/velocity each shade their own
    // dependency graph, common doesn't produce a standalone jar of its own)
    implementation(kotlin("stdlib"))

    // Adventure -- compileOnly, not implementation: both paper-api and velocity-api already
    // bring a compatible Adventure runtime with them, common must never bundle/shade its own
    // copy that could end up shadowing (or conflicting with) whichever one the host plugin's
    // classpath actually provides at runtime.
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.17.0")

    // Test framework -- kotlin("test") resolves to JUnit5 + kotlin.test assertions
    testImplementation(kotlin("test"))
    // compileOnly isn't visible to the test source set by default -- tests exercising
    // Component/MiniMessage output need this repeated here explicitly
    testImplementation("net.kyori:adventure-api:4.17.0")
    testImplementation("net.kyori:adventure-text-minimessage:4.17.0")
}

tasks.test {
    useJUnitPlatform()
}

// ================= BUILD CONFIGURATION =================

// Java 21 to match paper/velocity's own target -- common is never run on its own, so it just
// follows whatever the strictest consumer needs.
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}
