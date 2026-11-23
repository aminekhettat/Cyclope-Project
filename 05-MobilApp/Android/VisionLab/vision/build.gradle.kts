plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
    jacoco
}
kotlin { jvmToolchain(17) }
dependencies { testImplementation(libs.junit) }
jacoco { toolVersion = "0.8.12" }
tasks.test {
    maxHeapSize = "384m"
    maxParallelForks = 1
}
tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
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
