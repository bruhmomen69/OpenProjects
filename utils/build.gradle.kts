plugins {
    id("buildsrc.convention.kotlin-jvm")
}

// Aggregator module - provides all utils submodules as a single dependency
dependencies {
    api(project(":utils:core"))
    api(project(":utils:configapi"))
    api(project(":utils:database"))
    api(project(":utils:translations"))
    api(project(":utils:menuapi"))
    api(project(":utils:itemapi"))
}
