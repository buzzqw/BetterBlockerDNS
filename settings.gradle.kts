pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Maven Central through Google's mirror. GitHub's runners are
        // regularly rate-limited by repo.maven.apache.org, which made the
        // build fail while resolving the Hilt plugin on pushes that did not
        // touch the build files. The mirror serves the same artifacts.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralMirror"
        }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "BetterBlockerDNS"
include(":app")
