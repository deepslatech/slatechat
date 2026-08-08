// Root project owns no source of its own -- it only declares the plugin versions once (`apply
// false`, each subproject opts into what it actually needs) and the conventions shared by every
// subproject, so `common`/`paper`/`velocity` don't each repeat a version number that then drifts.
plugins {
    kotlin("jvm") version "2.4.10" apply false
    kotlin("kapt") version "2.4.10" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}

// group/version come from gradle.properties -- root-level gradle.properties in a multi-project
// build only sets these on the ROOT project automatically, subprojects need it propagated
// explicitly or they'd each default back to Gradle's "unspecified".
subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()

        // Official PaperMC repository -- both paper module's Paper API and velocity module's
        // Velocity API are published here
        maven("https://repo.papermc.io/repository/maven-public/")

        // PlaceholderAPI repository (paper module only, but harmless to share)
        maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")

        // Scarsz Nexus (DiscordSRV, paper module only)
        maven("https://nexus.scarsz.me/content/groups/public/")

        // William278's repo (PAPIProxyBridge, velocity module only)
        maven("https://repo.william278.net/releases/")
    }
}
