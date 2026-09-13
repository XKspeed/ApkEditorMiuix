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

/** 打包结果 */
data class BuildResult(
    val outputName: String,
    val signed: Boolean,
    /** 产物位置：SAF content Uri 或本地文件绝对路径 */
    val outputPath: String = "",
)

/** Smali 目录树节点 */
data class SmaliTreeNode(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val dex: String,
    val children: List<SmaliTreeNode> = emptyList(),
)
