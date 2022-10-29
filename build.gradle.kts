import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

tasks.register<Zip>("zipDatapack") {
  archiveFileName.set("mcx.zip")
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
  from(layout.projectDirectory.dir("mcx.zip"))
}

tasks.withType<ProcessResources> {
  dependsOn(tasks.getByName("zipDatapack"))
  from(layout.buildDirectory.file("distributions/mcx.zip"))
}
