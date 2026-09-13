plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
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
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:0.9.4-rc01")

    // miuix 0.9.3 的 MiuixPopupHost 依赖此库（runtime scope 传递，需显式声明才能编译访问）
    // 提供 LocalNavigationEventDispatcherOwner，否则展开任意 Overlay 弹窗会闪退
    implementation("androidx.navigationevent:navigationevent-compose-android:1.1.2")

    // ==== 真实反编译引擎（v0.1 功能层）====
    // ARSCLib：纯 Java 直改二进制 resources.arsc 与 AXML（弃用 apktool+aapt2，规避 Android17 arsc 回编译问题）
    implementation("com.github.REAndroid:ARSCLib:V1.4.0")
    // smali/baksmali 2.5.2：DEX ↔ smali
    implementation("org.smali:baksmali:2.5.2")
    implementation("org.smali:smali:2.5.2")
    // apksig：APK 签名（v1+v2）
    implementation("com.android.tools.build:apksig:8.13.2")
    // BouncyCastle：Android 上生成自签名证书（签名密钥）
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // sora-editor：MT/NP 同款代码编辑器（语法高亮、行号）
    implementation("io.github.Rosemoe.sora-editor:editor:0.23.6")
    implementation("io.github.Rosemoe.sora-editor:language-java:0.23.6")

    // 图片查看（drawable 缩放）
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.davemorrissey.labs:subsampling-scale-image-view:3.10.0")

    // Markdown 渲染
    implementation("io.noties.markwon:core:4.6.2") {
        exclude(group = "androidx.vectordrawable", module = "vectordrawable")
        exclude(group = "androidx.vectordrawable", module = "vectordrawable-animated")
    }
}
