import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
}

kotlin {
    applyDefaultHierarchyTemplate()

    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    val xcf = XCFramework("SongFlipKit")
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SongFlipKit"
            xcf.add(this)
            isStatic = true
            linkerOpts("-lsqlite3")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("io.ktor:ktor-client-core:2.3.13")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("app.cash.sqldelight:runtime:2.0.2")
            implementation("app.cash.sqldelight:coroutines-extensions:2.0.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:2.3.13")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
        }
        androidMain.dependencies {
            implementation("io.ktor:ktor-client-okhttp:2.3.13")
            implementation("app.cash.sqldelight:android-driver:2.0.2")
            implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:2.3.13")
            implementation("app.cash.sqldelight:native-driver:2.0.2")
        }
    }
}

sqldelight {
    databases {
        create("SongFlipDatabase") {
            packageName.set("de.goork.songflip.core.db")
        }
    }
}

android {
    namespace = "de.goork.songflip.shared"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
