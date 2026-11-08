plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Each GitHub build gets a higher version number so a new APK installs over the old one.
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "io.github.thanhhfishyu.flazy"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.thanhhfishyu.flazy"
        minSdk = 30 // Android 11+: needed for AccessibilityService.takeScreenshot
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        // Real phones only; skipping emulator (x86) libraries roughly halves the APK size.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // A fixed key kept in the repo so every build can update the installed app
        // without uninstalling it first. Personal sideload use only.
        create("flazy") {
            storeFile = file("flazy-debug.jks")
            storePassword = "flazydebug"
            keyAlias = "flazy"
            keyPassword = "flazydebug"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("flazy")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("flazy")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    // Chinese OCR model bundled inside the APK: works fully offline.
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
}
