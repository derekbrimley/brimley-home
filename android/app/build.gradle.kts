import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "home.brimley"
    compileSdk = 34

    defaultConfig {
        applicationId = "home.brimley"
        minSdk = 33          // the DC-1 runs Android 13
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        // Set these in android/local.properties (not committed):
        //   home.apiBase=https://home.yourdomain.com
        //   home.apiToken=<the "tablet" secret from HOME_API_TOKENS>
        //   home.nightClock=false   (optional; keeps the home screen up after 8pm, for testing)
        //   home.spotifyClientId=<Spotify app client ID>  (optional; wakes Spotify before an album)
        val props = Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField("String", "API_BASE", "\"${props.getProperty("home.apiBase", "")}\"")
        buildConfigField("String", "API_TOKEN", "\"${props.getProperty("home.apiToken", "")}\"")
        buildConfigField("boolean", "NIGHT_CLOCK", props.getProperty("home.nightClock", "true"))
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${props.getProperty("home.spotifyClientId", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(files("libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(libs.gson)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kxml2)
}
