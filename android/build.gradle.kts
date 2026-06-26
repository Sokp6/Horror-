plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.horrorgame"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.horrorgame.awakening"
        minSdk = 24; targetSdk = 34; versionCode = 4; versionName = "4.0"
    }
    buildTypes {
        release { isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-box2d:1.12.1")
    implementation("com.badlogicgames.gdx:gdx-ai:1.8.2")
}
