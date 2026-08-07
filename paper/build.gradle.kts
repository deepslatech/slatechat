import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow")
}

// group/version/repositories/plugin versions come from the root build.gradle.kts (subprojects{}
// block + apply-false plugin declarations) -- one place for them instead of three copies drifting.

// ================= DEPENDENCIES =================
dependencies {
    // Paper API (official server API, no NMS required) - provided by the server at runtime
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")

    // Kotlin standard library (needed at runtime, gets shaded into the final jar)
    implementation(kotlin("stdlib"))

    // Shared platform-agnostic code (see common/build.gradle.kts) -- shaded into this module's
    // jar the same as the Kotlin stdlib above, paper never ships without its own copy of it.
    implementation(project(":common"))

    // PlaceholderAPI (provided by the server if installed)
    compileOnly("me.clip:placeholderapi:2.11.6")

    // DiscordSRV (provided by the server)
    compileOnly("com.discordsrv:discordsrv:1.28.0")

    // Test framework -- kotlin("test") resolves to JUnit5 + kotlin.test assertions,
    // no separate JUnit Jupiter dependency needed
    testImplementation(kotlin("test"))
    // compileOnly isn't visible to the test source set by default -- tests that need
    // Bukkit types (e.g. a fake Permissible) need this repeated here explicitly
    testImplementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
}

tasks.test {
    useJUnitPlatform()
}

// ================= BUILD CONFIGURATION =================

// Java 21 to match Paper's target - the Kotlin compiler follows the same toolchain
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// Resource filtering for paper-plugin.yml (${project.name} / ${project.version} placeholders,
// same ${...} syntax Maven resource filtering used, carried over unchanged from the old
// plugin.yml -- only the manifest's filename and internal shape changed (classic plugin.yml ->
// Paper's newer paper-plugin.yml loader format), done as part of this monorepo split since it's
// the same moment the paper module's packaging surface is already being touched).
tasks.processResources {
    // Declared as a tracked input, not just captured in the expand() closure below -- Gradle's
    // up-to-date check doesn't see into that closure, so without this a version-only bump in
    // gradle.properties (nothing else touched) left processResources considering its previous
    // output still current and skipped re-expanding paper-plugin.yml: the shaded jar's
    // *filename* picked up the new version (that comes from the archive task itself), but the
    // version: line actually baked into paper-plugin.yml inside it silently stayed on the old
    // one. Found this shipping 1.5.0's own jar with a 1.4.0 plugin.yml still inside -- same
    // trap, so the same guard carries over to the new manifest file.
    inputs.property("projectVersion", project.version.toString())
    // paper-plugin.yml references ${project.name} / ${project.version}, Maven-style. Deliberately
    // rootProject.name here, not project.name -- now that `paper` is a subproject, project.name
    // is "paper" (the Gradle module name), not "SlateChat" (the plugin's actual name, which is
    // what plugin.yml's ${project.name} always meant back when this was a single-module build
    // and the two happened to be the same string). Caught by actually unzipping a build's jar
    // and finding `name: paper` baked into paper-plugin.yml instead of `name: SlateChat`.
    filesMatching("paper-plugin.yml") {
        expand(mapOf("project" to mapOf("name" to rootProject.name, "version" to project.version)))
    }
}

// Shade the Kotlin runtime (and any other implementation deps) into the final jar.
// The plain `jar` task is disabled so only the shaded jar is produced, avoiding
// two tasks fighting over the same build/libs/SlateChat-Paper-<version>.jar output.
tasks.named<Jar>("jar") {
    enabled = false
}

tasks.shadowJar {
    // Subproject jars default their archiveBaseName to the *subproject's* name ("paper"), not
    // "SlateChat-Paper" -- pinned explicitly so both platform jars are distinguishable at a
    // glance (this one vs. velocity's SlateChat-Velocity-<version>.jar) once CI is building and
    // releasing both from the same tag.
    archiveBaseName.set("SlateChat-Paper")
    archiveClassifier.set("")
    // New with the monorepo split: this jar now shades two Kotlin module's worth of
    // META-INF/*.kotlin_module metadata (paper's own + common's), not just one -- shadowJar's
    // default EXCLUDE duplicates strategy risks silently dropping one before Shadow's own
    // Kotlin-metadata transformer gets to merge them (each file has a distinct module-qualified
    // name, so nothing here actually collides -- this just lets the transformer see all of them).
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
