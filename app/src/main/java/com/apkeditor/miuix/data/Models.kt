package com.apkeditor.miuix.data

/** APK 基本信息 */
data class ApkInfo(
    val fileName: String,
    val label: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val minSdk: Int,
    val targetSdk: Int,
    val permissions: List<String>,
    val mainActivity: String,
    val dexNames: List<String>,
    val fileSize: String,
    val resourceCount: Int,
)

/** DEX 文件条目 */
data class DexEntry(
    val name: String,
    val size: String,
)

/** ARSC 资源类型 */
data class ResourceTypeInfo(
    val type: String,
    val count: Int,
)

/** ARSC 资源条目 */
data class ResourceEntryInfo(
    val id: Int,
    val hexId: String,
    val name: String,
    val type: String,
    val value: String,
    val configs: List<String>,
    val variants: List<ResourceVariant> = emptyList(),
)

/** ARSC 资源配置变体（default / -L / -R / night / hdpi 等） */
data class ResourceVariant(
    val qualifiers: String,
    val valueType: String,
    val displayValue: String,
    val rawData: Int,
)

/** APK 内的 XML 文件 */
data class XmlFileInfo(
    val path: String,
    val size: String,
)

/** APK 内容预览条目（zip 直接解压，快） */
data class ApkEntry(
    val path: String,
    val size: Long,
)

/** smali 目录树节点（NP 管理器风格） */
data class SmaliTreeNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val dex: String = "",
    val children: List<SmaliTreeNode> = emptyList(),
)

/** 打包结果 */
data class BuildResult(
    val outputName: String,
    val signed: Boolean,
    /** 产物位置：SAF content Uri 或本地文件绝对路径 */
    val outputPath: String = "",
)

/** smali 类条目（"所有类"列表页用，跨 DEX 合并的扁平列表） */
data class SmaliClassEntry(
    /** 所属 DEX，如 classes2.dex */
    val dex: String,
    /** smali 文件相对路径，如 smali/com/x/Foo.smali */
    val filePath: String,
    /** 点分全名，如 com.x.Foo */
    val className: String,
)

/** smali 类详情：类头信息 + 方法列表（类详情页用） */
data class SmaliClassDetail(
    /** 点分全名 */
    val className: String,
    /** 类访问标志，如 "public final" */
    val access: String,
    /** 父类点分名，如 java.lang.Object */
    val superName: String,
    /** 实现的接口点分名列表 */
    val interfaces: List<String>,
    /** 方法列表（按文件中出现顺序） */
    val methods: List<SmaliMethodInfo>,
)

/** smali 方法条目 */
data class SmaliMethodInfo(
    /** 方法序号：文件中第 index 个 .method 块（0 起），单方法编辑按此定位 */
    val index: Int,
    /** 完整声明行（".method public foo(I)V"），保存时用于校验方法未挪位 */
    val header: String,
    /** 访问标志，如 "public static" */
    val access: String,
    /** 方法名，如 foo / <init> */
    val name: String,
    /** 参数与返回值原型，如 (Ljava/lang/String;)V */
    val proto: String,
)
