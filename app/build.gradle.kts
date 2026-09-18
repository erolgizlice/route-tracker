import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// MAPS_API_KEY comes from the git-ignored local.properties, or from the environment on CI.
// Read through providers so that changing the key invalidates the configuration cache.
val mapsApiKey: String = providers
    .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText
    .map { text ->
        Properties().apply { load(text.reader()) }.getProperty("MAPS_API_KEY")?.trim()?.takeIf { it.isNotEmpty() }
    }
    .orElse(providers.environmentVariable("MAPS_API_KEY"))
    .getOrElse("")
    .trim()

// Release signing comes from ~/.gradle/gradle.properties on the author's machine, never from the
// repository. When the properties are absent the signing config is not created at all, so a clone still
// builds `assembleRelease` - unsigned (D26). Absolute paths only; `file()` does not expand `~`.
val releaseSigning = listOf(
    "RT_RELEASE_STORE_FILE",
    "RT_RELEASE_STORE_PASSWORD",
    "RT_RELEASE_KEY_ALIAS",
    "RT_RELEASE_KEY_PASSWORD",
).map { providers.gradleProperty(it) }
val hasReleaseSigning: Boolean = releaseSigning.all { it.isPresent && it.get().isNotBlank() }

android {
    namespace = "com.erolgizlice.routetracker"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        applicationId = "com.erolgizlice.routetracker"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        // Without a key the Maps SDK renders a blank grey map; the UI explains that instead.
        buildConfigField("boolean", "HAS_MAPS_API_KEY", mapsApiKey.isNotEmpty().toString())
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val (store, storePass, alias, keyPass) = releaseSigning.map { it.get() }
                storeFile = file(store)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Unsigned without the properties: `assembleRelease` still has to work in a fresh clone.
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
        // Release with the demo hooks: same signature, same R8, plus the src/demo sources that can feed
        // mock locations and seed a route. Only this build type gets that code (D26).
        create("demo") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
        // Release, with one difference: it is signed with the debug key, so the Maps key restricted to the
        // debug certificate still works. `initWith` copies everything else - minify, resource shrinking,
        // the ProGuard files, not debuggable - so the two cannot drift apart, which is what makes the
        // startup and stress numbers from this build worth anything (D26).
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            // The library modules have no benchmark build type; use their release variant.
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.data)
    implementation(projects.feature.tracking)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    // The library itself ships in every variant through :data, which is where the fused provider comes
    // from. What is demo-only is this compile dependency and the receiver in src/demo that uses it, so
    // no other variant contains code that can set a mock location (D26).
    "demoImplementation"(libs.play.location)   // the accessor does not exist yet for a build type created above
}
