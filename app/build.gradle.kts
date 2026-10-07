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
        versionCode = 2
        versionName = "0.11"
    }

    buildTypes {
        release {
            // R8：开启代码压缩与资源压缩
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 刻意不配 signingConfig：产出未签名包，由 CI 用 apksigner 精确只打 V2 签名。
            // 这样签名算法完全可控，且密钥不需要进仓库/不需要 Gradle 属性注入。
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // ── 打包瘦身 ──
    //
    // 背景：R8 只优化代码，shrinkResources 只处理 res/，**两者都管不到依赖 jar 里的
    // 根级资源文件** —— 这些资源会原样进 APK，是体积浪费的主要来源。
    //
    // ⚠️ 绝不能排除 frameworks/** —— ARSCLib 的 InternalFrameworks 在回编 XML 时会用
    //    getResourceAsStream("/frameworks/android/android-XX.apk") 读取平台资源表来解析
    //    android:* 属性，排除后回编必然失败。
    //
    // 说明：resources.excludes 只作用于**非 class 资源**（class 已编译进 dex），
    // 因此这里的排除不影响任何类的加载。
    packaging {
        resources {
            excludes += "META-INF/*"
            // 依赖 jar 内嵌的 Java 源码文件（如 eclipse jdt 的注解源码），运行期无用
            excludes += "src/**"
            // ANTLR 的代码生成模板（*.stg）：只有"运行时生成 parser"才需要，本工程不用
            excludes += "org/antlr/codegen/**"
            // ⚠️ 绝不能排除 tables/** —— 这是 JRuby jcodings 的运行时数据表。
            // language-textmate 内嵌 oniguruma，其 OnigRegExp/OnigString 直接使用
            // jcodings/specific/UTF8Encoding；而 UnicodeEncoding 的静态初始化会经
            // org.jcodings.util.ArrayReader 以 getResourceAsStream("/tables/*.bin") 读取它。
            // 缺表 → oniguruma 初始化抛异常 → TextMateLanguage.create 失败 →
            // EditorLanguages.smali() 返回 null → 编辑器静默退化为「无语法高亮」。
            // （曾因误判「语法文件不含 \p{...} 即可删」而排除过此目录，导致高亮全失。）
            //
            // BouncyCastle 的后量子密码（PQC）查找表，约 1.15MB。
            // 本工程只用到 BC 的证书构造与签名（X500Name / JcaX509v3CertificateBuilder /
            // JcaContentSignerBuilder / JcaX509CertificateConverter），不涉及后量子算法。
            excludes += "org/bouncycastle/pqc/**"
        }
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
    // ⚠️ 不能降级：activity 从 1.13.0 起才依赖并装配 androidx.navigationevent，
    // 从而在 View 树上提供 ViewTreeNavigationEventDispatcherOwner。
    // 1.11.0 的 POM 里完全没有 navigationevent 依赖 → 无人注入 dispatcher →
    // LocalNavigationEventDispatcherOwner 恒为 null → App.kt 的 NavigationBackHandler
    // 抛 IllegalStateException（No NavigationEventDispatcher was provided）→ 启动即崩。
    // 与 miuix-example 保持同一版本组合（activity 1.13.0 + navigationevent 1.1.2）。
    implementation("androidx.activity:activity-compose:1.13.0")
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

    // ARSCLib：本地 jar，由上游 main 分支（commit 089ec68）自行编译。
    //
    // 为何不用 Maven 上的 V1.4.0：该 tag 缺提交 31d559ff78（*XML Support DYNAMIC_REFERENCE
    // and DYNAMIC_ATTRIBUTE data types #108*）。缺该修复时，动态资源转换不会保留动态标签，
    // 而是把它错误转成静态资源，导致回编译产物在设备上致命报错。
    //
    // 为何不用 JitPack 指向 commit：JitPack 产出的是 jar，而 jar **不会自动携带** ARSCLib 自带的
    // proguard 规则（见 src/main/resources/META-INF/proguard/arsclib.pro）。改用本地 jar 后，
    // 对应规则在 app/proguard-rules.pro 里显式声明（org.xmlpull.v1 / AttributeSet /
    // XmlResourceParser），避免 R8 裁剪掉 AXML 解析所依赖的接口。
    //
    // 重新编译方式：拉 https://github.com/REAndroid/ARSCLib main 分支，
    //   ./gradlew clean build -x test   → 取 build/libs/ARSCLib-1.4.0.jar 覆盖 app/libs/ARSCLib.jar
    implementation(files("libs/ARSCLib.jar"))
    implementation("org.smali:baksmali:2.5.2")
    implementation("org.smali:smali:2.5.2")
    implementation("com.android.tools.build:apksig:8.13.2")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")

    // sora-editor：只保留编辑器核心 + TextMate 语法支持。
    // language-java 未使用（代码里零引用），它会把 Eclipse JDT / ANTLR / ICU 数据表打进来。
    implementation("io.github.Rosemoe.sora-editor:editor:0.23.6")
    implementation("io.github.Rosemoe.sora-editor:language-textmate:0.23.6")
}
