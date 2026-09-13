package com.apkeditor.miuix.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * APK 磁盘缓存管理器（对标 NP 管理器：同一 APK 只反编译一次）。
 *
 * 缓存根目录 cacheDir/apkcache/，每个 APK 一个子目录，目录名 = APK 内容 SHA-1 前 8 字节。
 * 子目录内：input.apk（原 APK 副本）、work/（反编译产物）。
 * 打开同一 APK 命中已有目录则直接复用；内容变化则哈希变化走新目录。
 *
 * 内存树缓存由 [RealApkDataService] 持有。清除缓存时需同时清磁盘与内存，
 * 故通过 [registerMemoryCleaner] 注册服务层的内存清理回调（全局共享，跨实例生效）。
 */
class ApkCacheManager(private val context: Context) {

    fun rootDir(): File = File(context.cacheDir, "apkcache")

    /** 计算 APK 文件内容哈希（SHA-1 前 8 字节 = 16 hex） */
    fun hashOf(file: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        val digest = md.digest()
        val sb = StringBuilder()
        for (i in 0 until 8) sb.append(String.format("%02x", digest[i]))
        return sb.toString()
    }

    /** 为某 APK 准备缓存目录（命中复用，未命中拷贝） */
    fun prepare(srcApk: File): ApkCacheEntry {
        val hash = hashOf(srcApk)
        val dir = File(rootDir(), hash)
        dir.mkdirs()
        val inputApk = File(dir, "input.apk")
        if (!inputApk.exists() || inputApk.length() != srcApk.length()) {
            srcApk.inputStream().use { ins ->
                FileOutputStream(inputApk).use { fos -> ins.copyTo(fos) }
            }
        }
        return ApkCacheEntry(hash = hash, dir = dir, inputApk = inputApk)
    }

    fun workDir(entry: ApkCacheEntry): File {
        val d = File(entry.dir, "work")
        d.mkdirs()
        return d
    }

    /** 缓存总量（字节） */
    fun totalSize(): Long {
        val root = rootDir()
        if (!root.exists()) return 0L
        var total = 0L
        root.walkTopDown().forEach { if (it.isFile) total += it.length() }
        return total
    }

    /** 人类可读的缓存大小 */
    fun totalSizeText(): String {
        val b = totalSize()
        val kb = b / 1024.0
        return when {
            b < 1024 -> "$b B"
            kb < 1024 -> String.format("%.1f KB", kb)
            else -> String.format("%.1f MB", kb / 1024.0)
        }
    }

    /** 清除全部磁盘缓存 + 已注册的内存缓存 */
    fun clearAll(): Boolean {
        val root = rootDir()
        val ok = if (!root.exists()) true else root.deleteRecursively()
        synchronized(globalCleaners) {
            globalCleaners.forEach { runCatching { it() } }
        }
        return ok
    }

    fun clear(hash: String): Boolean {
        val dir = File(rootDir(), hash)
        return if (!dir.exists()) true else dir.deleteRecursively()
    }

    companion object {
        /** 全局内存清理回调（跨实例共享：设置页与服务层是不同实例） */
        private val globalCleaners = mutableListOf<() -> Unit>()

        /** 服务层注册内存缓存清理回调（清 smaliCache / smaliTreeCache） */
        fun registerMemoryCleaner(cleaner: () -> Unit) {
            synchronized(globalCleaners) { globalCleaners.add(cleaner) }
        }

        /** smali 目录名：classes.dex → smali，classesN.dex → smali_classesN */
        fun smaliDirName(dexName: String): String {
            val n = dexName.removePrefix("classes").removeSuffix(".dex")
            return if (n.isEmpty()) "smali" else "smali_classes$n"
        }
    }
}

/** 一个 APK 的缓存条目 */
data class ApkCacheEntry(
    val hash: String,
    val dir: File,
    val inputApk: File,
)
