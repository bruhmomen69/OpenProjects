import io.papermc.paperweight.userdev.ReobfArtifactConfiguration

plugins {
    kotlin("jvm")
    alias(libs.plugins.paperweightUserdev)
}

dependencies {
    implementation(project(":RegionRestore:PaperMC"))
    paperweight.paperDevBundle("26.2.build.112-stable")
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

    val mappings = File.createTempFile("mappings", ".tiny")
    mappingsFile.set(mappings)
    mappings.deleteOnExit()
    enabled = true
}
