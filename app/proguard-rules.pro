# APK Editor Miuix — R8 规则
#
# 目标：release 开启 R8（minify + shrinkResources），但绝不能把「靠反射/序列化/资源名」
# 工作的类混淆掉。以下规则按「锚定类名/成员名」的思路写，不做条件 keep。

# ───────────────── 应用入口 ─────────────────
-keep class com.apkeditor.miuix.MainActivity { *; }
-keep class com.apkeditor.miuix.MainActivity$* { *; }

# ───────────────── kotlinx.serialization ─────────────────
# Route 是 @Serializable 的密封接口 + 数据对象/数据类，导航栈靠它做保存/恢复。
# 生成的 $serializer 必须按名字保留，否则运行期 ClassNotFoundException。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.apkeditor.miuix.**$$serializer { *; }
-keepclassmembers class com.apkeditor.miuix.** {
    *** Companion;
}
-keepclasseswithmembers class com.apkeditor.miuix.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# 密封接口实现类（Route 的各 data object / data class）
-keep class com.apkeditor.miuix.ui.Route { *; }
-keep class com.apkeditor.miuix.ui.Route$* { *; }

# ───────────────── 反射用到的方法 ─────────────────
# RealApkDataService 通过反射调用 AssetManager.addAssetPath(String) 加载 APK 资源表
-keepclassmembers class android.content.res.AssetManager {
    public int addAssetPath(java.lang.String);
}

# ───────────────── Compose / Miuix ─────────────────
# Compose 运行时大量依赖函数名与 lambda 类；库自带规则，这里兜底 lambda 与编译器产物
-dontwarn androidx.compose.**
-keepclassmembers class **$Compose* { *; }
-keep class androidx.compose.runtime.** { *; }
-dontwarn top.yukonga.miuix.**

# ───────────────── 第三方库（自带规则不全的兜底） ─────────────────
# apksig：签名走内部反射/服务发现
-dontwarn com.android.apksig.**
-keep class com.android.apksig.** { *; }

# BouncyCastle：Provider 靠类名反射注册
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# smali/baksmali + dexlib2：解析 dex，含服务与注解驱动
-dontwarn org.jf.**
-keep class org.jf.** { *; }
-keep class org.smali.** { *; }

# ARSCLib：解析 resources.arsc，含 JNI 无关的反射式 XML 工厂
-dontwarn com.reandroid.**
-keep class com.reandroid.** { *; }

# sora-editor：编辑器/语言扩展按接口与反射装载
-dontwarn io.github.rosemoe.**
-keep class io.github.rosemoe.** { *; }

# Markwon / 图片库
-dontwarn io.noties.markwon.**
-dontwarn com.github.chrisbanes.photoview.**
-dontwarn com.davemorrissey.labs.subscaleview.**

# ───────────────── 平台 / JDK 宽容 ─────────────────
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.security.**
-dontwarn javax.crypto.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.debug.**

# 保留行号（崩溃栈可读；release 也方便定位）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
