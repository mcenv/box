import javax.xml.parsers.DocumentBuilderFactory

plugins {
  kotlin("jvm") version "1.8.10"
  kotlin("plugin.serialization") version "1.8.10"
  id("org.jetbrains.kotlinx.kover") version "0.6.1"
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
  compileOnly("com.mojang:brigadier:1.0.18")
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

tasks.register("kover") {
  group = "verification"
  dependsOn("koverXmlReport")
  doLast {
    var node = DocumentBuilderFactory
      .newInstance()
      .newDocumentBuilder()
      .parse(file("$buildDir/reports/kover/xml/report.xml"))
      .firstChild
      .firstChild
    while (node != null) {
      if (node.nodeName == "counter") {
        if (node.attributes.getNamedItem("type").textContent == "INSTRUCTION") {
          val missed = node.attributes.getNamedItem("missed").textContent.toLong()
          val covered = node.attributes.getNamedItem("covered").textContent.toLong()
          val coverage = (covered * 100.0) / (missed + covered)
          exec {
            commandLine("bash", "-c", """echo "COVERAGE=%.1f"""".format(coverage), ">>", "\$GITHUB_ENV")
          }
          break
        }
      }
      node = node.nextSibling
    }
  }
}
