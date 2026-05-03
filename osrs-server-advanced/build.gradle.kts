import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    application
}

group = "com.osrs"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.ADOPTOPENJDK)
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    // RSProt is expected to be sourced from JitPack once the correct artifact
    // coordinates are available. The current project does not require the
    // library at compile time because packet handling is implemented locally.
}

val nettyVersion = "4.1.108.Final"


dependencies {
    // RSProt dependency is intentionally omitted for local compilation until
    // the published artifact becomes available.
    // implementation("com.github.blurite:rsprot:$rspRotVersion")

    // Netty - async network I/O
    implementation("io.netty:netty-all:$nettyVersion")

    // Kotlin coroutines for game loop
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")

    // Logging
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.3")
    implementation("ch.qos.logback:logback-classic:1.5.3")

    // Serialization (for config)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // YAML config
    implementation("com.charleskorn.kaml:kaml:0.57.0")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.osrs.server.ServerMainKt")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs += listOf("-Xcontext-receivers", "-opt-in=kotlin.RequiresOptIn")
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "com.osrs.server.ServerMainKt"
    }
    // Fat jar - include all dependencies
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.test {
    useJUnitPlatform()
}
