plugins {
    kotlin("jvm")
    alias(libs.plugins.paperweightUserdev)
}

dependencies {
    implementation(project(":RegionRestore:PaperMC"))
    paperweight.paperDevBundle("1.21.4-R0.1-SNAPSHOT")
    implementation(libs.kache)
    compileOnly(libs.nettyBuffer)
}

kotlin {
    jvmToolchain(21)
}
