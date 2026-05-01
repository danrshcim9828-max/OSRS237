import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    application
}

group = "com.osrs"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    // RSProt is published on JitPack from blurite/rsprot
    maven("https://maven.blurite.io/releases") // if published on own maven
}

val nettyVersion = "4.1.108.Final"
val rspRotVersion = "1.0.0-beta.4" // RSProt revision 237 compatible release

dependencies {
    // RSProt 237 - OSRS Protocol Library by blurite
    // RSProt provides typed packet definitions, codec, and Huffman support
    implementation("com.github.blurite:rsprot:$rspRotVersion")

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
