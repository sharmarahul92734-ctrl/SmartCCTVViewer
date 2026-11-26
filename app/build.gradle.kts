
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.smartcctv.viewer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartcctv.viewer"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }
}

dependencies {
    implementation("org.videolan.android:libvlc-all:3.6.0")
}
