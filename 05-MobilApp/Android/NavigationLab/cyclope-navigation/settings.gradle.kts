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

rootProject.name = "CyclopeNavigation"

// :navigation = the navigation brick (to be integrated in the final app)
// :app        = small test harness used to measure positioning and demo the guidance
include(":app", ":navigation", ":voicekit")
