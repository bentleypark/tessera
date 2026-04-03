pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // PREFER_PROJECT required: Kotlin/Wasm plugin registers Node.js/yarn repositories
    // at project level, which FAIL_ON_PROJECT_REPOS blocks.
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Tessera"
include(":tessera-core")
include(":tessera-glide")
include(":tessera-coil")
include(":sample")
include(":sample-desktop")
include(":sample-web")
