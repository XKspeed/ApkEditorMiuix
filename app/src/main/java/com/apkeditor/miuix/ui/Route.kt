package com.apkeditor.miuix.ui

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * Type-safe navigation keys for the app, backed by miuix-nav.
 * Each destination is a NavKey (data object/data class) and can be saved/restored in the back stack.
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object Home : Route

    @Serializable
    data object SavedApks : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object About : Route

    @Serializable
    data class ApkInfo(val apkPath: String) : Route

    @Serializable
    data class SmaliTree(val apkPath: String, val dexNames: List<String> = emptyList()) : Route

    @Serializable
    data class SmaliEdit(val apkPath: String, val dexName: String, val filePath: String) : Route

    /** 类详情页：类头信息 + 方法列表（点 smali 文件进入） */
    @Serializable
    data class SmaliClass(
        val apkPath: String,
        val dexName: String,
        val filePath: String,
        /** 上下文 DEX 列表（指南针 → 所有类列表用，避免重复反编译其它 DEX） */
        val dexNames: List<String> = emptyList(),
    ) : Route

    /** “所有类”列表页（类详情页顶栏指南针进入，快速切换类） */
    @Serializable
    data class SmaliClassList(val apkPath: String, val dexNames: List<String> = emptyList()) : Route

    /** 单方法编辑页（方法列表点击进入，保存只回写该方法块） */
    @Serializable
    data class SmaliMethod(
        val apkPath: String,
        val dexName: String,
        val filePath: String,
        /** 第 N 个 .method 块（0 起） */
        val methodIndex: Int,
        /** 方法声明行，保存时校验方法未挪位 */
        val methodHeader: String,
    ) : Route

    @Serializable
    data class ArscTypes(val apkPath: String) : Route

    @Serializable
    data class ArscEntries(val apkPath: String, val type: String) : Route

    @Serializable
    data object XmlFiles : Route

    @Serializable
    data class XmlEdit(val path: String) : Route

    @Serializable
    data object UiSettings : Route

    @Serializable
    data object ThirdPartyLicenses : Route
}
