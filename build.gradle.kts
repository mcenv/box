plugins {
  kotlin("jvm") version "1.8.0"
  kotlin("plugin.serialization") version "1.8.0"
  application
}

version = "0.1.0"

repositories {
  mavenCentral()
  maven("https://libraries.minecraft.net")
}

dependencies {
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.19.0")
  implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
  implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
  implementation("org.ow2.asm:asm:9.4")
  compileOnly("com.mojang:brigadier:1.0.500")
  testImplementation(kotlin("test"))
}

sourceSets {
  main {
    resources {
      exclude("std/out", "std/server.properties")
    }
  }
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(17)
}

application {
  mainClass.set("mcx.cli.MainKt")
  applicationDefaultJvmArgs += "-Djdk.attach.allowAttachSelf=true"
}

tasks.withType<Jar> {
  manifest {
    attributes("Agent-Class" to "mcx.script.Agent")
  }
}

@Suppress("UnstableApiUsage")
tasks.withType<ProcessResources> {
  filesMatching("version") {
    expand("version" to version)
  }
}
