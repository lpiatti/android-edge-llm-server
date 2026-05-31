// Application Module Build File
// ------------------------------
// This file configures the build settings, compilation variables, 
// and external dependencies for our Android App.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    // Unique package identifier for resources and tooling
    namespace = "com.edge.llm.server"
    
    // The Android SDK version used to compile this application
    compileSdk = 34

    defaultConfig {
        // Unique application ID deployed to devices (cannot overlap with other apps on the device)
        applicationId = "com.edge.llm.server"
        
        // Minimum SDK set to 29 (Android 10.0) as per docs/daemon-stability-guidelines.md
        // Ensures suitability for older, dedicated repurposable hardware.
        minSdk = 29
        
        // Target SDK set to 34 (Android 14.0) to comply with modern background service policies
        targetSdk = 34
        
        // Version codes for app updates
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Target Java 17, standard for modern AGP and Kotlin Android builds
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // AndroidX Core KT Extensions: Standard Kotlin extensions for common Android classes
    implementation("androidx.core:core-ktx:1.12.0")
    
    // AppCompat: Ensures UI elements and Activity features are backwards-compatible
    implementation("androidx.appcompat:appcompat:1.6.1")
    
    // Material Components: Essential library for standard Google UI components
    implementation("com.google.android.material:material:1.11.0")

    // Ktor Server Core & CIO Engine
    implementation("io.ktor:ktor-server-core:2.3.11")
    implementation("io.ktor:ktor-server-cio:2.3.11")
    
    // Ktor Content Negotiation and Kotlinx JSON serialization
    implementation("io.ktor:ktor-server-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.11")

    // Official LiteRT-LM Android SDK for on-device inference
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")

    // Basic Testing Frameworks (needed for standard template structure)
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
