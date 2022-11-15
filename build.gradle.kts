import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
  repositories {
    mavenCentral()
  }

  dependencies {
    classpath("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
  }
}

plugins {
  kotlin("jvm") version "1.7.21"
  kotlin("plugin.serialization") version "1.7.21"
  application
}

version = "0.1.0"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.4")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.18.0")
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

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "17"
}

application {
  mainClass.set("mcx.cli.MainKt")
}

@Suppress("UnstableApiUsage")
tasks.withType<ProcessResources> {
  filesMatching("version") {
    expand("version" to version)
  }
}
