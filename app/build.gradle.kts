import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val appVersionName = "1.10.3"
val computedVersionCode = run {
    val cleanVersion = appVersionName.substringBefore("-").substringBefore("+")
    val parts = cleanVersion.split(".").mapNotNull { it.toIntOrNull() }
    val major = parts.getOrElse(0) { 1 }
    val minor = parts.getOrElse(1) { 0 }
    val patch = parts.getOrElse(2) { 0 }
    (major * 10000) + (minor * 100) + patch
}

val localProps = Properties()
val localPropsFile = rootProject.file("local.properties")
if (localPropsFile.exists()) {
    localProps.load(FileInputStream(localPropsFile))
}

val revenueCatApiKey = System.getenv("REVENUECAT_API_KEY")
    ?: localProps.getProperty("revenuecat.api.key")
    ?: ""

val aptabaseAppKey = System.getenv("APTABASE_APP_KEY")
    ?: localProps.getProperty("aptabase.app.key")
    ?: ""

val aptabaseHost = System.getenv("APTABASE_HOST")
    ?: localProps.getProperty("aptabase.host")
    ?: "https://telemetry-apps.goork.de"

android {
    namespace = "de.goork.songflip"
    compileSdk = 36

    defaultConfig {
        applicationId = "de.goork.songflip"
        minSdk = 26
        targetSdk = 36
        versionCode = computedVersionCode
        versionName = appVersionName

        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
        buildConfigField("String", "APTABASE_APP_KEY", "\"$aptabaseAppKey\"")
        buildConfigField("String", "APTABASE_HOST", "\"$aptabaseHost\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                val keystoreFile = file(keystorePath)
                if (keystoreFile.exists()) {
                    storeFile = keystoreFile
                    storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD") ?: ""
                    keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: ""
                    keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: ""
                } else {
                    logger.warn("SIGNING_KEYSTORE_PATH is set to '$keystorePath' but file does not exist.")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank() && file(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    bundle {
        language {
            enableSplit = false
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(files("libs/shazamkit-android-release.aar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.coil.compose)

    // Networking & ShazamKit runtime dependencies
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.org.json)

    // In-App Purchases & Billing (RevenueCat - Google Play Billing Library 8+)
    implementation(libs.revenuecat.purchases)

    // Google Play In-App Review API
    implementation(libs.play.review)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
