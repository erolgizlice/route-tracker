import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure JVM on purpose: the domain model and the 100 m rule compile without the Android SDK,
// so the rule is unit-tested on the JVM and no Android type can leak into the domain.
plugins {
    alias(libs.plugins.kotlin.jvm)
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
    // Flow is part of the public repository contracts.
    api(libs.coroutines.core)

    testImplementation(libs.junit)
}
