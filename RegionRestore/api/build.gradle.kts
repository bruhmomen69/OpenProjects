plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Expose RegionRestore API types and interfaces
    api(project(":RegionRestore:PaperMC"))

    // Paper/Bukkit API for World, Player, etc.
    compileOnly(libs.paperApi)
}
