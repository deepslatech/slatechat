plugins {
    // Lets Gradle auto-provision a JDK 21 toolchain if one isn't already installed locally
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "SlateChat"

// common   -- shared between paper and velocity, platform-agnostic only (no Bukkit/Paper or
//             Velocity API types allowed in here -- see common/build.gradle.kts).
// paper    -- today's plugin, unchanged behavior, just relocated under this module.
// velocity -- new proxy plugin, empty for now (Stage 1 of the monorepo split just proves the
//             multi-module build; real Velocity code lands in a later stage).
include("common", "paper", "velocity")
