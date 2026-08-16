plugins {
    id("java")
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.acclash"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    // Built against the Spigot API only -- deliberately no NMS.
    //
    // Spigot and Paper use different mappings (Paper runs Mojang-mapped, Spigot does not), so a
    // plugin that calls NMS directly cannot be a single jar that runs on both. The Spigot server
    // artifact is also unpublished by licence, meaning an NMS build would require every developer
    // and every CI run to execute BuildTools first.
    //
    // Nothing here needs NMS: vanilla already tracks a dirty rectangle per map and sends only the
    // changed region, and that path is reachable through MapCanvas.
    compileOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")

    // Display entity transformations use joml types. The server provides it at runtime; spigot-api
    // does not re-export it, so it is needed on the compile classpath only. Version matches what
    // the 26.2 server ships.
    compileOnly("org.joml:joml:1.10.8")

    // Tests cover the pure geometry and lookup code only -- the maths that decides where a part
    // sits, where a look ray lands and which key a character is. None of it needs a server, and
    // all of it is the kind of thing that breaks silently: a wrong transform still renders, it
    // just renders in the wrong place.
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testCompileOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")
    testRuntimeOnly("org.spigotmc:spigot-api:26.2-R0.1-SNAPSHOT")
    testImplementation("org.joml:joml:1.10.8")
}

java {
    // Toolchain is provisioned by the foojay resolver in settings.gradle.kts, so a clean clone
    // builds without a matching local JDK.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    compileJava {
        // spigot-api itself is Java 17 bytecode. 21 is a safe floor: every server capable of
        // running 26.2 is on at least that, and it keeps the jar usable on Spigot, Paper and forks.
        options.release.set(21)
    }

    jar {
        archiveBaseName.set("VMComputers")
    }

    test {
        useJUnitPlatform()
        testLogging { events("failed") }
    }

    runServer {
        // Paper is used for the dev loop because it can be downloaded automatically. The plugin
        // itself only touches the Spigot API, so Spigot compatibility holds by construction.
        minecraftVersion("26.2")
    }

    register("printVersion") {
        doLast {
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
