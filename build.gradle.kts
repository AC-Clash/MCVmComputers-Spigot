plugins {
    id("java")
    id("io.papermc.paperweight.userdev") version "1.5.16-SNAPSHOT"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.acclash"
version = "0.1.0"

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://papermc.io/repo/repository/maven-public/") }
    maven { url = uri("https://hub.spigotmc.org/nexus/content/groups/public/") }
}

dependencies {
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
    implementation("org.javassist:javassist:3.29.2-GA")
    implementation("jnetpcap:jnetpcap:1.4.r1425-1g")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    // Configure reobfJar to run when invoking the build task
    assemble {
        dependsOn(reobfJar)
    }

    compileJava {
        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release.set(21)
    }

    register("printVersion") {
        doLast {
            // Assuming 'project' is an instance of Project
            println(project.version)
        }
    }
}


tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
