// Top-level build file. Versions chosen for compatibility with Android Studio Ladybug (2024.2) and newer.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.dokka") version "2.0.0" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1" apply false
}

// ---------------------------------------------------------------------------
// Single-source version management (bump2version equivalent, project rule).
// Canonical source: gradle.properties (cyclopeVersionName / cyclopeVersionCode).
// verifyVersionSingleSource = anti-drift gate run in CI.
// bumpVersion = automated bump, same semantics as bump2version parts.
// ---------------------------------------------------------------------------
val canonicalVersionFile = file("gradle.properties")

fun readCanonicalVersion(): Pair<String, Int> {
    val props = java.util.Properties().apply { canonicalVersionFile.reader().use { load(it) } }
    val name =
        props.getProperty("cyclopeVersionName")
            ?: error("cyclopeVersionName missing from gradle.properties (canonical version source)")
    val code =
        props.getProperty("cyclopeVersionCode")?.toIntOrNull()
            ?: error("cyclopeVersionCode missing or not an integer in gradle.properties (canonical version source)")
    return name to code
}

tasks.register("verifyVersionSingleSource") {
    group = "versioning"
    description = "Anti-drift gate: canonical version lives in gradle.properties and no module hardcodes its own versionName/versionCode."
    val buildFiles = subprojects.map { it.file("build.gradle.kts") } + file("build.gradle.kts")
    doLast {
        val (name, code) = readCanonicalVersion()
        require(Regex("""^\d+\.\d+\.\d+$""").matches(name)) {
            "cyclopeVersionName '$name' must follow MAJOR.MINOR.PATCH"
        }
        require(code > 0) { "cyclopeVersionCode must be a positive integer, got $code" }
        val hardcoded = Regex("""version(Name|Code)\s*=\s*("|[0-9])""")
        val offenders =
            buildFiles.filter { it.exists() }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    if (hardcoded.containsMatchIn(line) && !line.contains("findProperty")) {
                        "${f.path}:${i + 1}: ${line.trim()}"
                    } else null
                }
            }
        if (offenders.isNotEmpty()) {
            error("Version drift detected - versionName/versionCode must come from gradle.properties:\n" + offenders.joinToString("\n"))
        }
        println("Version OK: cyclopeVersionName=$name, cyclopeVersionCode=$code (single source: gradle.properties)")
    }
}

tasks.register("bumpVersion") {
    group = "versioning"
    description = "Bump the canonical version in gradle.properties. Usage: ./gradlew bumpVersion -Pbump=patch|minor|major|code"
    doLast {
        val kind = (findProperty("bump") as String?) ?: "patch"
        val (name, code) = readCanonicalVersion()
        val parts = name.split(".").map { it.toInt() }.toMutableList()
        when (kind) {
            "major" -> { parts[0]++; parts[1] = 0; parts[2] = 0 }
            "minor" -> { parts[1]++; parts[2] = 0 }
            "patch" -> { parts[2]++ }
            "code" -> { /* versionCode-only bump */ }
            else -> error("Unknown bump kind '$kind' (expected patch|minor|major|code)")
        }
        val newName = parts.joinToString(".")
        val newCode = code + 1
        var text = canonicalVersionFile.readText()
        text = text.replace(Regex("""cyclopeVersionName=.*"""), "cyclopeVersionName=$newName")
        text = text.replace(Regex("""cyclopeVersionCode=.*"""), "cyclopeVersionCode=$newCode")
        canonicalVersionFile.writeText(text)
        println("Bumped ($kind): $name (code $code) -> $newName (code $newCode) in ${canonicalVersionFile.path}")
    }
}
