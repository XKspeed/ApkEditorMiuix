plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidLibrary {
        namespace = "com.apkeditor.miuix.shared"
        compileSdk = 37
        minSdk = 35
    }

    sourceSets {
        commonMain.dependencies {
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
    }
}
