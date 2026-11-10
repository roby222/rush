import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "io.github.roby222.rush"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.roby222.rush"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
    }
    // The release key lives outside the repo; its path and passwords come from local.properties (never in git).
    signingConfigs {
        localProps.getProperty("RELEASE_STORE_FILE")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        // Debug builds (Android Studio) read the developer's key; a release carries none: its user enters their own.
        debug { buildConfigField("String", "TOMTOM_API_KEY", "\"${localProps.getProperty("TOMTOM_API_KEY", "")}\"") }
        release {
            buildConfigField("String", "TOMTOM_API_KEY", "\"\"")
            signingConfig = signingConfigs.findByName("release")
        }
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
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.2.0")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
