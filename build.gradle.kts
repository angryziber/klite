import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm") version "1.6.0"
}

allprojects {
  group = rootProject.name
  version = "0.1"
}

subprojects {
  apply(plugin = "kotlin")

  repositories {
    mavenCentral()
  }

  dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.5.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
    testImplementation("org.assertj:assertj-core:3.21.0")
    testImplementation("io.mockk:mockk:1.12.1")
  }

  sourceSets {
    named("main") {
      java.srcDirs("src")
      resources.srcDirs("src").exclude("**/*.kt")
    }
    named("test") {
      java.srcDirs("test")
      resources.srcDirs("test").exclude("**/*.kt")
    }
  }

  tasks.withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = "11"
      javaParameters = true
    }
  }

  tasks.jar {
    archiveBaseName.set("${rootProject.name}-${project.name}")
    manifest {
      attributes(mapOf(
        "Implementation-Title" to archiveBaseName,
        "Implementation-Version" to project.version
      ))
    }
  }

  tasks.test {
    useJUnitPlatform()
  }
}
