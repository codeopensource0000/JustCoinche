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
