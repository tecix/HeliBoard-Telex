// Pure Kotlin/JVM Vietnamese Telex engine, lexicon and suggestion helpers.
// Kept free of Android and HeliBoard dependencies so it never conflicts with upstream.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // tests read src/main/assets of the app module (vnlexicon.bin)
    workingDir = projectDir
}
