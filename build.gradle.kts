plugins {
    id("java")
    id("buildsrc.convention.kotlin-jvm")
    id("com.gradleup.shadow") version "9.3.0" apply false
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19" apply false
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

allprojects {
    group = "bruh.regionrestore"
    version = "1.0-SNAPSHOT"

    repositories {
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc-repo"
        }
        mavenCentral()
        maven("https://oss.sonatype.org/content/groups/public/") {
            name = "sonatype"
        }
        maven("https://repo.codemc.io/repository/nms/") {
            name = "nms"
        }
        maven("https://repo.spaceio.xyz/repository/maven-public/") {
            name = "spaceio-repo"
        }
        maven("https://repo.extendedclip.com/releases/") {
            name = "placeholderapi"
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
        compilerOptions {
            javaParameters = true
        }
    }

    tasks.withType<JavaCompile> {
        options.compilerArgs.add("-parameters")
    }
}
