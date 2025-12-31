plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    // Expose RegionRestore API types and interfaces
    api(project(":RegionRestore:PaperMC"))

    // Paper/Bukkit API for World, Player, etc.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
}
