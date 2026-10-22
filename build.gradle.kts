plugins {
    // Gives the root project a `check` task, so that `./gradlew check` runs
    // static analysis alongside every module's tests.
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
}

tasks.named("check") {
    dependsOn(tasks.named("detekt"))
}

// Static analysis for Kotlin: long functions, deep nesting, `!!`, over-broad
// catches, dead code. It fails the build on any finding; there is no baseline
// yet because there is no debt yet, and the point is to keep it that way.
detekt {
    source.setFrom(files("engine/src/main/kotlin", "engine/src/test/kotlin"))
    config.setFrom(files("config/detekt.yml"))
    buildUponDefaultConfig = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        sarif.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
    }
}
