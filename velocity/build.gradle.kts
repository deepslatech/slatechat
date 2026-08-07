import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Placeholder module, deliberately near-empty -- this monorepo split is landing before there's
// any real Velocity plugin code to put here. The shadow jar packaging below is already wired up
// (so CI can build and release a SlateChat-Velocity jar alongside paper's from day one), but the
// jar itself has nothing Velocity-specific in it yet. Once a real Velocity plugin lands: its
// `@Plugin` annotation is processed by a compile-time annotation processor, which in a Kotlin
// module needs `id("org.jetbrains.kotlin.kapt")` + `kapt("com.velocitypowered:velocity-api:...")`
// alongside the usual `compileOnly` of the same artifact -- `compileOnly` alone isn't enough,
// unlike a plain Paper `compileOnly(paper-api)`.
plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(project(":common"))
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
