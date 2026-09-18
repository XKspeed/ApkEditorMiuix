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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
    implementation("top.yukonga.miuix.kmp:miuix-core-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-shader-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4-rc01")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.4-rc01")

    implementation("androidx.navigationevent:navigationevent-compose-android:1.1.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")

    implementation("com.github.REAndroid:ARSCLib:V1.4.0")
    implementation("org.smali:baksmali:2.5.2")
    implementation("org.smali:smali:2.5.2")
    implementation("com.android.tools.build:apksig:8.13.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    implementation("io.github.Rosemoe.sora-editor:editor:0.23.6")
    implementation("io.github.Rosemoe.sora-editor:language-java:0.23.6")
    implementation("io.github.Rosemoe.sora-editor:language-textmate:0.23.6")

    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.davemorrissey.labs:subsampling-scale-image-view:3.10.0")

    implementation("io.noties.markwon:core:4.6.2") {
        exclude(group = "androidx.vectordrawable", module = "vectordrawable")
        exclude(group = "androidx.vectordrawable", module = "vectordrawable-animated")
    }

    implementation("dev.chrisbanes.haze:haze:1.7.3")
}
