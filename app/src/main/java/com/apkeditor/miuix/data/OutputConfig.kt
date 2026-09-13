package com.apkeditor.miuix.data

import android.content.Context
import android.os.Environment

/**
 * 输出目录配置：回编译后的 APK 统一输出位置。
 *
 * 输出模式（优先级从高到低）：
 *  1. 公共目录：授予「所有文件访问」（MANAGE_EXTERNAL_STORAGE）后输出到
 *     `/storage/emulated/0/ApkEditorMiuix/output/`（Android 11+ 需系统授权）
 *  2. SAF 目录：用户在设置页通过 SAF 选择的目录（保存 tree Uri + 持久权限）
 *  3. 默认目录：App 私有外部存储 `getExternalFilesDir(null)/output`（无需任何权限）
 */
object OutputConfig {

    @Volatile
    private var appContext: Context? = null

    private const val PREFS = "output_config"
    private const val KEY_TREE = "output_tree_uri"
    private const val KEY_DIR_NAME = "output_dir_name"
    private const val KEY_USE_PUBLIC = "use_public_dir"

    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
        }
    }

    private fun prefs(): android.content.SharedPreferences {
        val ctx = appContext ?: error("OutputConfig.init() 必须先调用")
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** 是否已授予「所有文件访问」权限（Android 11+） */
    fun hasAllFilesAccess(context: Context): Boolean =
        runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)

    /** 公共输出目录（需已授予所有文件访问权限） */
    fun publicOutputDir(context: Context): java.io.File {
        val dir = java.io.File(Environment.getExternalStorageDirectory(), "ApkEditorMiuix/output")
        dir.mkdirs()
        return dir
    }

    /** 默认输出目录（App 私有外部存储，无需权限） */
    fun defaultOutputDir(context: Context): java.io.File {
        val dir = java.io.File(context.getExternalFilesDir(null), "output")
        dir.mkdirs()
        return dir
    }

    // ---- SAF 目录（备选方案） ----

    fun getTreeUri(): String? = prefs().getString(KEY_TREE, null)
    fun getDirName(): String? = prefs().getString(KEY_DIR_NAME, null)
    fun setTreeUri(uri: String?, name: String?) {
        prefs().edit().putString(KEY_TREE, uri).putString(KEY_DIR_NAME, name).apply()
    }

    /** 计算当前输出目录的显示名 */
    fun displayName(context: Context): String = when {
        hasAllFilesAccess(context) ->
            "公共目录 /storage/emulated/0/ApkEditorMiuix/output"
        getTreeUri() != null -> getDirName() ?: "已选择目录（SAF）"
        else -> "默认目录（应用私有）"
    }
}
