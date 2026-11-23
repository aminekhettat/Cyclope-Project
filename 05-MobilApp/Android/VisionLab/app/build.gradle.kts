plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
}
android {
    namespace = "org.blindsystems.cyclope.visionlab"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.blindsystems.cyclope.visionlab"
        minSdk = 26
        targetSdk = 35
        versionName = providers.gradleProperty("cyclopeVersionName").get()
        versionCode = providers.gradleProperty("cyclopeVersionCode").get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "tflite" }
}
dependencies {
    implementation(project(":vision"))
    implementation(libs.activity)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.litert)
    implementation(libs.litert.gpu)
}
