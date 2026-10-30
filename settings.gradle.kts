pluginManagement {
    repositories {
        if (providers.gradleProperty("grove.centralMirror").orNull == "google") maven("https://maven-central.storage-download.googleapis.com/maven2")
        google(); mavenCentral(); gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (providers.gradleProperty("grove.centralMirror").orNull == "google") maven("https://maven-central.storage-download.googleapis.com/maven2")
        google(); mavenCentral()
    }
}
rootProject.name = "Grove"
include(":app")
