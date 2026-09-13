plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "META-INF/*"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

// 强制解决旧版vectordrawable namespace冲突
configurations.all {
    exclude(group = "androidx.vectordrawable", module = "vectordrawable")
    exclude(group = "androidx.vectordrawable", module = "vectordrawable-animated")
}

dependencies {
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // Miuix - Xiaomi HyperOS style UI
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:0.9.4-rc01")

    // miuix 0.9.3 的 MiuixPopupHost 依赖此库（runtime scope 传递，需显式声明才能编译访问）
    implementation("androidx.navigationevent:navigationevent-compose-android:1.1.2")

    // kotlinx-serialization（miuix-nav 需要）
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Compose Multiplatform Resources（painterResource 需要）
    implementation("org.jetbrains.compose.components:components-resources:1.7.3")
}
