plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.ktlint)
}

tasks.register("verifyVersionSingleSource") {
    group = "verification"
    doLast {
        val name = providers.gradleProperty("cyclopeVersionName").get()
        val code = providers.gradleProperty("cyclopeVersionCode").get().toInt()
        require(Regex("""\d+\.\d+\.\d+""").matches(name) && code > 0)
        val hardcoded = Regex("""version(Name|Code)\s*=\s*("|[0-9])""")
        subprojects.forEach { module ->
            require(!hardcoded.containsMatchIn(module.file("build.gradle.kts").readText())) {
                "Read versionName/versionCode from gradle.properties, not literals."
            }
        }
        println("Single source verified: $name ($code)")
    }
}

tasks.register("bumpVersion") {
    group = "versioning"
    doLast {
        val file = file("gradle.properties")
        val parts = providers.gradleProperty("cyclopeVersionName").get().split(".").map(String::toInt).toMutableList()
        when (providers.gradleProperty("bump").getOrElse("patch")) {
            "major" -> {
                parts[0]++
                parts[1] = 0
                parts[2] = 0
            }
            "minor" -> {
                parts[1]++
                parts[2] = 0
            }
            "patch" -> parts[2]++
            "code" -> Unit
            else -> error("Expected major, minor, patch or code")
        }
        file.writeText(
            file.readText()
                .replace(Regex("cyclopeVersionName=.*"), "cyclopeVersionName=${parts.joinToString(".")}")
                .replace(Regex("cyclopeVersionCode=.*"), "cyclopeVersionCode=${providers.gradleProperty("cyclopeVersionCode").get().toInt() + 1}"),
        )
    }
}
