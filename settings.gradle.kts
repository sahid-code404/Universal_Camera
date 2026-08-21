pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Universal_Camera"

include(":app")
include(":core:model")
include(":core:settings")
include(":camera:discovery")
include(":camera:camera2")
include(":processing:api")
include(":processing:native")
include(":storage")
include(":updater")
