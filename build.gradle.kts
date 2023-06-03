import org.jetbrains.dokka.gradle.DokkaTask
import javax.xml.parsers.DocumentBuilderFactory

plugins {
  kotlin("jvm") version "1.9.0-Beta"
  kotlin("plugin.serialization") version "1.9.0-Beta"
  kotlin("plugin.allopen") version "1.9.0-Beta"
  id("org.jetbrains.dokka") version "1.8.10"
  id("org.jetbrains.kotlinx.kover") version "0.7.1"
  id("org.jetbrains.kotlinx.benchmark") version "0.4.8"
  application
}

buildscript {
  dependencies {
    classpath("org.jetbrains.dokka:dokka-base:1.8.10")
  }
}

version = "0.1.0"

repositories {
  mavenCentral()
  maven("https://libraries.minecraft.net")
}

dependencies {
  implementation("com.mojang:brigadier:1.1.8")
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.0")
  implementation("org.ow2.asm:asm:9.5")
  implementation("org.ow2.asm:asm-commons:9.5")
  implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-properties:1.5.1")
  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.8")
}

sourceSets {
  main {
    resources {
      exclude("core/.mcx")
    }
  }
}

kotlin {
  jvmToolchain(19)
}

tasks.withType<@Suppress("UnstableApiUsage") ProcessResources> {
  filesMatching("version") {
    expand("version" to version)
  }
}

application {
  mainClass.set("mcx.cli.MainKt")
}

tasks.test {
  useJUnitPlatform()
}

benchmark {
  targets {
    register("test")
  }
  configurations {
    getByName("main") {
      reportFormat = "json"
    }
  }
}

allOpen {
  annotation("org.openjdk.jmh.annotations.State")
}

tasks.withType<DokkaTask>().configureEach {
  outputDirectory.set(projectDir.resolve("docs/book/api"))
}

tasks.register("kover") {
  group = "verification"
  dependsOn("koverXmlReport")
  doLast {
    var node = DocumentBuilderFactory
      .newInstance()
      .newDocumentBuilder()
      .parse(file("$buildDir/reports/kover/report.xml"))
      .firstChild
      .firstChild
    while (node != null) {
      if (node.nodeName == "counter") {
        if (node.attributes.getNamedItem("type").textContent == "INSTRUCTION") {
          val missed = node.attributes.getNamedItem("missed").textContent.toLong()
          val covered = node.attributes.getNamedItem("covered").textContent.toLong()
          val coverage = (covered * 100.0) / (missed + covered)
          File(System.getenv("GITHUB_ENV")).appendText("COVERAGE=%.1f%n".format(coverage))
          break
        }
      }
      node = node.nextSibling
    }
  }
}
