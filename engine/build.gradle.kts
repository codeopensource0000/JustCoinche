import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// The engine is plain Kotlin with no Android dependency: it builds and is
// tested on any JVM, and the Android app will consume it as a library.
//
// It is compiled by JDK 25 but against the Java 17 API and to Java 17
// bytecode, which is what the Android toolchain accepts. Without the release
// flag, code could call a JDK 25 API that compiles here and crashes on a phone.
kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Plays complete games between computer players and writes their records,
// to analyse the computer players' behaviour without anyone playing by hand:
// ./gradlew :engine:simulate --args="20 regles 42" (games, players, seed).
tasks.register<JavaExec>("simulate") {
    group = "application"
    description = "Plays games between computer players and writes their records."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("justcoinche.arena.SimulationKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}

// A duplicate match between two AIs of the catalog:
// ./gradlew :engine:duel --args="ia2 ia1 1000 7" (AI A, AI B, deals, seed).
tasks.register<JavaExec>("duel") {
    group = "application"
    description = "Plays a duplicate match between two AIs of the catalog."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("justcoinche.arena.DuelKt")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
