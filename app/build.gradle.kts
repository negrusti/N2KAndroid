plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull?.toIntOrNull()
val ciVersionName = providers.gradleProperty("ciVersionName").orNull

android {
    namespace = "com.gregor.n2kandroid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gregor.n2kandroid"
        minSdk = 23
        targetSdk = 35
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

