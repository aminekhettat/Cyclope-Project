plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.blindsystems.cyclope.navlab"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.blindsystems.cyclope.navlab"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Single-source version: canonical values live in gradle.properties (see root build.gradle.kts).
        versionCode = (project.findProperty("cyclopeVersionCode") as String).toInt()
        versionName = project.findProperty("cyclopeVersionName") as String
    }

    buildTypes {
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

dependencies {
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    implementation(project(":navigation"))
    implementation("androidx.core:core-ktx:1.13.1") // FileProvider for "Share CSV"
}
