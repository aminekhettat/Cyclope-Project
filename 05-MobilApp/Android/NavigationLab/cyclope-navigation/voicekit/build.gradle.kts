plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
    jacoco
}
android {
    namespace = "org.blindsystems.cyclope.voicekit"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(libs.androidx.core)
    testImplementation(libs.junit)
}
jacoco { toolVersion = "0.8.12" }
tasks.withType<Test>().configureEach {
    maxHeapSize = "384m"
    maxParallelForks = 1
}

// Framework adapters need real Android validation; these gates cover ALL reusable pure core classes.
val coreClasses =
    fileTree(layout.buildDirectory.dir("tmp/kotlin-classes/debug")) {
        include("org/blindsystems/cyclope/voicekit/core/**")
    }
val coreExecution =
    fileTree(layout.buildDirectory) {
        include("jacoco/testDebugUnitTest.exec", "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    }
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coreClasses)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(coreExecution)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")
    classDirectories.setFrom(coreClasses)
    executionData.setFrom(coreExecution)
    violationRules {
        rule {
            listOf("INSTRUCTION", "LINE", "BRANCH", "METHOD").forEach { metric ->
                limit {
                    counter = metric
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }
}

tasks.register("verifySharedBoundary") {
    group = "verification"
    description = "Reject vision/app imports, project coupling and microphone permission in shared voicekit."
    doLast {
        val forbiddenImport = Regex("""(?m)^\s*import\s+org\.blindsystems\.cyclope\.(vision|visionlab)(\.|\s|$)""")
        val forbiddenFiles =
            fileTree("src").matching { include("**/*.kt", "**/*.java") }.files.filter {
                forbiddenImport.containsMatchIn(it.readText())
            }
        require(forbiddenFiles.isEmpty()) { "voicekit must not import vision/app: $forbiddenFiles" }
        val coupled =
            configurations.flatMap { it.dependencies.withType(ProjectDependency::class.java) }
                .map { it.dependencyProject.path }.filter { it == ":vision" || it == ":app" }
        require(coupled.isEmpty()) { "voicekit must not depend on vision/app: $coupled" }
        require(!file("src/main/AndroidManifest.xml").readText().contains("RECORD_AUDIO")) {
            "STT is a contract only: voicekit must not request microphone permission in V1"
        }
        println("Shared boundary verified: no vision/app dependency/import and no microphone permission")
    }
}
