# Installation (Folia-focused)

This page summarizes the official Installation guide, tailored for Folia.

## Dependencies

Gradle (Kotlin DSL/Groovy shown with versions from the MCCoroutine docs; update to latest as needed):

```groovy
dependencies {
    implementation("com.github.shynixn.mccoroutine:mccoroutine-folia-api:2.22.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-folia-core:2.22.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.x.x")
}
```

Notes:
- MCCoroutine is built against Kotlin 1.3.x but does not ship Kotlin; you can use newer Kotlin and kotlinx-coroutines.
- Replace 1.x.x with your chosen version compatible with your Kotlin.

## Shading vs runtime libraries

- Folia supports declaring plugin library dependencies in plugin.yml.
- Recommended for Folia servers:

plugin.yml:

```yaml
libraries:
  - com.github.shynixn.mccoroutine:mccoroutine-folia-api:2.22.0
  - com.github.shynixn.mccoroutine:mccoroutine-folia-core:2.22.0
```

If targeting other servers or special packaging, you may shade dependencies into your jar using your build tool.

## Sanity check

After adding dependencies, try a minimal test in your plugin’s onEnable to verify `launch {}` runs and `delay(...)` works without blocking the server.

See also: the sample plugins in the MCCoroutine repository.
