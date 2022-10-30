import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.FilterReader
import java.io.Reader
import java.io.StringReader

buildscript {
  repositories {
    mavenCentral()
  }

  dependencies {
    classpath("org.jetbrains.kotlinx:kotlinx-serialization-json:1.4.1")
  }
}

plugins {
  kotlin("jvm") version "1.7.20"
  application
}

version = "0.1.0"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.17.0")
  testImplementation(kotlin("test"))
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

class JsonMinifier(input: Reader) : FilterReader(StringReader(Json.encodeToString(Json.decodeFromString<JsonElement>(input.readText()))))

class McfunctionMinifier(input: Reader) : FilterReader(
  StringReader(mutableListOf<String>().let { output ->
    input
      .readLines()
      .forEach {
        val trimmed = it.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith('#')) {
          output += trimmed
        }
      }
    output.joinToString("\n")
  })
)

tasks.register<ProcessResources>("minifyDatapack") {
  from(layout.projectDirectory.dir("datapack"))
  into(layout.buildDirectory.dir("tmp/datapack"))
  filesMatching(
    listOf(
      "**/*.json",
      "**/*.mcmeta",
    )
  ) {
    filter(JsonMinifier::class)
  }
  filesMatching("**/*.mcfunction") {
    filter(McfunctionMinifier::class)
  }
}

tasks.register<Zip>("zipDatapack") {
  dependsOn(tasks.getByName("minifyDatapack"))
  from(layout.buildDirectory.dir("tmp/datapack"))
  archiveFileName.set("mcx.zip")
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
}

tasks.getByName<ProcessResources>("processResources") {
  dependsOn(tasks.getByName("zipDatapack"))
  from(layout.buildDirectory.file("distributions/mcx.zip"))
}
