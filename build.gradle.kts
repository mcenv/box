import javax.xml.parsers.DocumentBuilderFactory

plugins {
  kotlin("jvm") version "1.9.0"
  kotlin("plugin.serialization") version "1.9.0"
  kotlin("plugin.allopen") version "1.9.0"
  id("org.jetbrains.kotlinx.kover") version "0.7.2"
  id("org.jetbrains.kotlinx.benchmark") version "0.4.8"
  application
}

version = "0.1.0"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.0")
  implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.7.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-properties:1.5.1")
  testImplementation(kotlin("test"))
  testImplementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.8")
}

sourceSets {
  main {
    resources {
      srcDirs("packs")
      exclude("core/.mcx", "pack/.mcx", "test")
    }
  }
  test {
    resources {
      srcDirs("packs")
    }
  }
}

kotlin {
  jvmToolchain(20)
}

tasks.withType<ProcessResources> {
  filesMatching("version") {
    expand("version" to version)
  }
}

application {
  mainClass.set("mcx.util.CliKt")
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
