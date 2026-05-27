// Settings file to configure the overall Android Project structure
// -----------------------------------------------------------------

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
        google()            // Google's repository containing Android SDK libraries and Gradle Plugins
        mavenCentral()      // Standard Java dependency repository for standard packages
    }
}

rootProject.name = "Android Edge LLM Server"

// Include the ':app' module which contains our application code
include(":app")
