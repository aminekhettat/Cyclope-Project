plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
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

    testOptions { unitTests.isIncludeAndroidResources = true }

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
    implementation(project(":voicekit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    implementation("androidx.core:core-ktx:1.13.1") // FileProvider for "Share CSV"
}

// Do not rewrite Taoyu's existing activity or smoke tests for style in this integration.
ktlint {
    filter {
        exclude("**/MainActivity.kt", "**/SmokeInstrumentedTest.kt")
    }
}

// Reuse NavigationLab's existing JVM Android shadows; no emulator or real positioning hardware.
tasks.withType<Test>().configureEach {
    maxHeapSize = "768m"
    maxParallelForks = 1
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
