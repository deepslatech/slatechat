import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// `kapt`, not just `compileOnly` (and not Java's `annotationProcessor`, which doesn't apply to
// Kotlin sources): velocity-api's `@Plugin` annotation is read by a compile-time annotation
// processor that generates the velocity-plugin.json manifest baked into the jar.
plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":common"))

    // Velocity API -- provided by the proxy at runtime, same compileOnly convention as paper's
    // paper-api. Pinned to the 3.x line deliberately: 4.0.0/4.1.0-SNAPSHOT both require JVM 25,
    // this project targets 21 (see jvmToolchain below).
    compileOnly("com.velocitypowered:velocity-api:3.5.1")
    kapt("com.velocitypowered:velocity-api:3.5.1")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// Same shading setup as paper/build.gradle.kts -- see that file's comments for why each of
// these is here (disabling the plain `jar` task, the archiveBaseName override, and the
// duplicates strategy for the Kotlin module metadata Shadow needs to merge across common+this
// module).
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.shadowJar {
    archiveBaseName.set("SlateChat-Velocity")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
