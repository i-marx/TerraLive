plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.terralive.wallpapers"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.terralive.wallpapers"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0"
    }
    signingConfigs {
        create("release") {
            val ksPath = System.getenv("TERRA_KEYSTORE")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("TERRA_KEYSTORE_PASS")
                keyAlias = System.getenv("TERRA_KEY_ALIAS") ?: "terra"
                keyPassword = System.getenv("TERRA_KEYSTORE_PASS")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("TERRA_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
