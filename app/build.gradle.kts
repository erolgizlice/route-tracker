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
}
