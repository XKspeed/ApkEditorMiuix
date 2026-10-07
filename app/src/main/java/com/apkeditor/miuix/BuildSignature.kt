package com.apkeditor.miuix

import android.content.Context
import android.content.pm.PackageManager
import java.security.MessageDigest

/**
 * 构建来源识别：靠「签名证书指纹」判断当前安装包是官方正式版还是自签测试版。
 *
 * 为什么不直接用 BuildConfig.DEBUG：
 *  测试机装的多是**自签的 release 包**，这时 BuildConfig.DEBUG 是 false，
 *  测试界面就再也进不去了。按签名区分更贴合实际工作流：
 *    - 官方密钥签名（CI 发布）→ 正式版 → 隐藏测试界面
 *    - 其它任何密钥（debug / 自签测试版）→ 显示测试界面
 *
 * 判断依据：PackageManager 取当前包的签名证书 DER 字节，做 SHA-256，与内置的
 * 官方指纹白名单比对。**拿不到官方密钥就进不了这个名单**，所以官方版没有测试入口。
 */
object BuildSignature {

    /**
     * 官方（正式版）签名证书的 SHA-256 指纹白名单，小写十六进制、无分隔符。
     *
     * 由 keystore/release.p12 导出核对：
     *   openssl x509 -in cert.pem -outform DER | openssl dgst -sha256
     *
     * ⚠️ 更换签名密钥时必须同步更新此处，否则正式版会误显示测试界面。
     */
    private val OFFICIAL_SHA256 = setOf(
        "1cc42bdda2f59ca2c82abd72356c086c12b06fd1adc18e0440ba5e75e2fb999b",
    )

    /** 结果缓存：签名在进程生命周期内不会变，而该值会在组合期间被读取。 */
    @Volatile
    private var cachedShowTestUi: Boolean? = null

    /**
     * 当前包签名证书的 SHA-256 指纹集合（多签时多个）。
     * 读取失败返回空集 —— 调用方据此走「保守显示」分支。
     */
    private fun currentSha256(context: Context): Set<String> = try {
        // minSdk 35，直接走 API 28+ 的 SigningInfo 路径
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val si = info.signingInfo
        val certs = when {
            si == null -> emptyArray()
            si.hasMultipleSigners() -> si.apkContentsSigners
            else -> si.signingCertificateHistory
        }
        certs.mapNotNull { sig ->
            runCatching { sha256Hex(sig.toByteArray()) }.getOrNull()
        }.toSet()
    } catch (e: Exception) {
        emptySet()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private const val HEX = "0123456789abcdef"

    /** 是否为官方正式版（用官方密钥签名）。 */
    fun isOfficialRelease(context: Context): Boolean {
        val current = currentSha256(context)
        return current.isNotEmpty() && current.any { it in OFFICIAL_SHA256 }
    }

    /**
     * 是否显示测试界面。
     *
     * 语义：**官方签名 → 隐藏；其它一律显示**。
     * 签名读取失败时保守返回 true（显示），避免调试时入口莫名消失。
     */
    fun showTestUi(context: Context): Boolean {
        cachedShowTestUi?.let { return it }
        return (!isOfficialRelease(context)).also { cachedShowTestUi = it }
    }
}
