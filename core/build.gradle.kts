plugins {
    kotlin("jvm")
    `java-library`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    api(project(":api"))

    // Concurrency
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.flywaydb:flyway-core:10.10.0")
    implementation("org.flywaydb:flyway-database-postgresql:10.10.0")
    runtimeOnly("org.postgresql:postgresql:42.7.3")
    runtimeOnly("org.xerial:sqlite-jdbc:3.45.2.0")

    // Configuration
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    // Crypto
    implementation("com.auth0:java-jwt:4.4.0")
}