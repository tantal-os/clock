plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "app.tantal.clock"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        minSdk = 36
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
}
