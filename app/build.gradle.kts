plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.timesince.app"
    compileSdk = 35

    // A fixed debug key, so every build is signed the same way and a new APK installs
    // over the old one without uninstalling. This key is public (it is in the repository),
    // so use your own private key for anything you publish.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.timesince.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "3.0"
    }

    buildTypes {
        // The APK that is published is the "debug" build type, because it is signed with the fixed key above.
        // It is not marked debuggable: a debuggable app looks like a developer test build to Android's
        // scanners and is easier to tamper with. Same key, so updates still install over old versions.
        debug {
            isDebuggable = false
        }
        release {
            isMinifyEnabled = false
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
