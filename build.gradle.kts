// Root Build Gradle File
// ----------------------
// This file declares the versions of core plugins used by the modules in the project.
// We do not apply them to the root project (using "apply false"), making them available for submodules.

plugins {
    // Android Application Gradle Plugin (AGP) - Orchestrates Android-specific packaging and compilation
    id("com.android.application") version "8.2.2" apply false
}
