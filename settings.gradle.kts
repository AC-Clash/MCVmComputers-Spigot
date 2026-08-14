rootProject.name = "VMComputers"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven {
            url = uri("https://repo.papermc.io/repository/maven-public/")
        }
    }
}

plugins {
    // Lets Gradle download the JDK the toolchain asks for instead of requiring it to already be
    // installed, so the build works from a clean clone and in CI without a matching local JDK.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
