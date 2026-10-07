# APK Editor Miuix — R8 规则
#
# 目标：release 开启 R8（minify + shrinkResources），但绝不能把「靠反射/序列化/资源名/泛型签名」
# 工作的代码裁剪或混淆坏。规则一律用「类名锚定」写，不使用 extends/implements 条件 keep
# （R8 full-mode 下条件 keep 对库代码不可靠）。
#
# 排查提示：本工程的失败多为**静默失败**（runCatching 捕获后只弹提示），
# 现象类似"功能没反应/提示不支持"，而不是崩溃。日志见设置 → 日志。

# ═════════════════ 属性保留（最重要） ═════════════════
# ARSCLib / dexlib2 / BouncyCastle / apksig 都依赖这些属性做反射与泛型解析。
# 缺 Signature 会导致泛型信息丢失（XML 工厂、编解码器按泛型实例化时失败，
# 表现为 ARSCLib 抛 "getNamespacePrefix() not supported" 之类"不支持"错误）。
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ═════════════════ 应用入口 ═════════════════
-keep class com.apkeditor.miuix.MainActivity { *; }
-keep class com.apkeditor.miuix.MainActivity$* { *; }

# ═════════════════ kotlinx.serialization ═════════════════
# Route 是 @Serializable 密封接口 + 数据对象/数据类，导航栈靠它保存/恢复。
# 生成的 $serializer 必须按名字保留，否则运行期 ClassNotFoundException。
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

# ═════════════════ 反射调用点 ═════════════════
# RealApkDataService 通过反射 newInstance() + addAssetPath(String) 加载 APK 资源表。
# 注意 newInstance() 走的是**无参构造**，构造器也必须保留。
-keepclassmembers class android.content.res.AssetManager {
    public <init>();
    public int addAssetPath(java.lang.String);
}

# ═════════════════ Compose / Miuix ═════════════════
-dontwarn androidx.compose.**
-keepclassmembers class **$Compose* { *; }
-keep class androidx.compose.runtime.** { *; }
-dontwarn top.yukonga.miuix.**

# ═════════════════ 第三方库 ═════════════════
# apksig：签名走内部反射 / 服务发现
-dontwarn com.android.apksig.**
-keep class com.android.apksig.** { *; }

# BouncyCastle：Provider 按类名反射注册，且依赖泛型签名
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# smali / baksmali / dexlib2：解析与汇编 dex，含注解驱动与泛型
-dontwarn org.jf.**
-keep class org.jf.** { *; }
-keep class org.smali.** { *; }

# ARSCLib：resources.arsc 与二进制 XML 编解码。
# 该库大量使用泛型 + 反射式工厂（XMLFactory.newPullParser 等），
# 且要保留 DYNAMIC_REFERENCE / DYNAMIC_ATTRIBUTE 相关编解码器，
# 因此整体 keep 类与成员，不做裁剪。
-dontwarn com.reandroid.**
-keep class com.reandroid.** { *; }

# ── ARSCLib 自带的 proguard 规则（jar 不会自动携带，必须在这里手写）──
# 来源：ARSCLib/src/main/resources/META-INF/proguard/arsclib.pro
#
# 这三条是关键：ARSCLib 解析 AXML 时会拿到平台的 XmlResourceParser 并调用其
# getNamespacePrefix() / getAttributeNamespace() 等接口方法。若这些接口被 R8 裁剪，
# 就会抛 "getNamespacePrefix() not supported" 之类的「不支持」错误 ——
# 表现为「打开 XML 报不支持」，而不是崩溃，极难定位。
-keep class org.xmlpull.v1.** { *; }
-keep class android.util.AttributeSet { *; }
-keep class android.content.res.XmlResourceParser { *; }

# sora-editor：编辑器与语言扩展按接口 / 反射装载；TextMate 语法资源依赖 assets
-dontwarn io.github.rosemoe.**
-keep class io.github.rosemoe.** { *; }
# TextMate 依赖的 grammar / 主题资源（反射读取，禁止压缩重命名）
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }

# Markwon / 图片库
-dontwarn io.noties.markwon.**
-dontwarn com.github.chrisbanes.photoview.**
-dontwarn com.davemorrissey.labs.subscaleview.**

# ═════════════════ 平台 / JDK 宽容 ═════════════════
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn javax.security.**
-dontwarn javax.crypto.**
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.debug.**

# ═════════════════ 资源说明 ═════════════════
# shrinkResources 只作用于 res/ 下的资源；assets/（含 sora-editor 的 textmate 语法文件）
# 不受资源压缩影响，无需额外 keep 指令。
