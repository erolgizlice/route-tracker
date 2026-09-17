plugins {
    alias(libs.plugins.android.library)
    // A Compose library module needs both the compiler plugin and buildFeatures.compose.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.erolgizlice.routetracker.feature.tracking"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 26
    }
    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Depends on the domain only. It never sees :data; :app wires implementations in via Koin.
    implementation(projects.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.play.maps)
    implementation(libs.maps.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.compose)

    testImplementation(libs.junit)
}
