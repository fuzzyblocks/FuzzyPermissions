plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "2.2.3"
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

kotlin {
    jvmToolchain(21)
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation(project(":core"))

    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
}

tasks {
    shadowJar {
        archiveClassifier.set("") // Remove the "-all" suffix
        
        relocate("com.zaxxer.hikari", "net.fuzzyblocks.fuzzypermissions.lib.hikari")
        relocate("org.flywaydb", "net.fuzzyblocks.fuzzypermissions.lib.flyway")
        relocate("com.auth0.jwt", "net.fuzzyblocks.fuzzypermissions.lib.jwt")
        relocate("org.spongepowered.configurate", "net.fuzzyblocks.fuzzypermissions.lib.configurate")
    }

    // Modern way to hook shadow into the build without creating cycles
    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion("1.21.1")
    }
}