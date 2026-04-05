import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar.Companion.shadowJar
import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    kotlin("jvm")
    alias(libs.plugins.paperweightUserdev)
}

dependencies {
    implementation(project(":RegionRestore:PaperMC"))
    paperweight.paperDevBundle("26.1.1.build.14-alpha")
    implementation(libs.kache)
    compileOnly(libs.nettyBuffer)
}

kotlin {
    jvmToolchain(25)
}

paperweight {
    reobfArtifactConfiguration.set(ReobfArtifactConfiguration.MOJANG_PRODUCTION)
}

tasks.reobfJar.configure {
    fromNamespace.set("mojang")
    toNamespace.set("mojang")

    val myFile = File.createTempFile("mappings", ".tiny")
    mappingsFile.set(myFile)
    myFile.deleteOnExit()
    enabled = true
}