plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions.jvmTarget = "21"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.components:components-resources:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")

            // Miuix - Xiaomi HyperOS style UI
            implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-squircle:0.9.3")
            implementation("top.yukonga.miuix.kmp:miuix-nav:0.9.4-rc01")

            // NavigationEvent（miuix popup 需要）
            implementation("androidx.navigationevent:navigationevent:1.1.2")
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
        }
    }
}

android {
    namespace = "com.apkeditor.miuix"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.apkeditor.miuix"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += "META-INF/*"
    }
}
