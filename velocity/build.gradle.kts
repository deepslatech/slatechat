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

    // SnakeYAML for network-channel.yml -- compileOnly, not shaded: velocity-api 3.5.1's own POM
    // already declares this exact version as a transitive dependency, so every Velocity proxy
    // already bundles it at runtime. Pinned to match, same reasoning as common/build.gradle.kts's
    // Adventure compileOnly split -- shading our own copy risks a classloader conflict with the
    // proxy's.
    compileOnly("org.yaml:snakeyaml:2.5")

    // PAPIProxyBridge -- soft dependency, same compileOnly convention as PlaceholderAPI/
    // DiscordSRV/ItemsAdder on the paper module: present on the runtime classpath only if the
    // server owner installed it as a proxy plugin, guarded at every call site via SoftDependency
    // (see velocity/.../integrations/) so network channels work identically without it, just
    // without placeholder resolution.
    compileOnly("net.william278:papiproxybridge:1.7.2")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

// version.txt gives the update checker something to compare against -- @Plugin's own version
// field is deliberately left unset (see SlateChatVelocity.kt, annotation args must be
// compile-time constants), so this is the one place this module's actual version is knowable at
// runtime. Same resource-filtering approach and up-to-date-check guard as paper-plugin.yml's own
// ${project.version} substitution in paper/build.gradle.kts -- see that file's comment for why
// inputs.property(...) is required here, not optional.
tasks.processResources {
    inputs.property("projectVersion", project.version.toString())
    filesMatching("version.txt") {
        expand(mapOf("project" to mapOf("version" to project.version)))
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
