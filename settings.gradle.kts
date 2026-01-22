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
        // Chaquopy 12.0.1+ moved to Maven Central - no longer need chaquo.com/maven
    }
}

rootProject.name = "Android-Math-Agent"
include(":app")
