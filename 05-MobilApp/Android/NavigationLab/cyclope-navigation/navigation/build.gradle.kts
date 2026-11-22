plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.dokka")
    id("org.jlleitschuh.gradle.ktlint")
    jacoco
}

// ---------------------------------------------------------------------------
// JaCoCo: unit-test coverage of the navigation brick (JVM tests only).
// jacocoTestReport produces XML+HTML under build/reports/jacoco/jacocoTestReport.
// jacocoTestCoverageVerification enforces the minimum INSTRUCTION coverage.
// ---------------------------------------------------------------------------
configure<JacocoPluginExtension> { toolVersion = "0.8.12" }

tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val coverageClassDirs =
    fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.class")
    }
val coverageExecFiles =
    fileTree(layout.buildDirectory) {
        include(
            "jacoco/testDebugUnitTest.exec",
            "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
        )
    }

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "JaCoCo XML+HTML coverage report for :navigation unit tests."
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClassDirs)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(coverageExecFiles)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    group = "verification"
    description = "Enforces the minimum instruction coverage for :navigation (see reports/jacoco)."
    dependsOn("jacocoTestReport")
    classDirectories.setFrom(coverageClassDirs)
    executionData.setFrom(coverageExecFiles)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                minimum = "0.65".toBigDecimal() // measured baseline 2026-11-22: 68.1% INSTRUCTION; threshold set just below
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ktlint: style-only static analysis. Existing code is not rewritten for style,
// so violations are reported (build/reports/ktlint) but do not fail the build
// until the team decides to enforce them (flip ignoreFailures to false).
// ---------------------------------------------------------------------------
configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    ignoreFailures.set(true)
}

android {
    namespace = "org.blindsystems.cyclope.navigation"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
    // JSON parsing of Nominatim / OSRM answers (only the JsonElement API: no compiler plugin needed)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
