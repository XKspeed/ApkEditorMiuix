package com.apkeditor.miuix.data

import android.content.Context
import android.net.Uri
import com.apkeditor.miuix.SavedApkStore
import com.android.apksig.ApkSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 「快速编辑」——APK 版本号（versionName / versionCode）读取与就地修改。
 *
 * 功能层移植自 ApkVerTool 的 AxmlPatch + ZipAlignWriter：
 *  1. 抽出 AndroidManifest.xml（二进制 AXML），用 [AxmlVersion] 读/改版本号
 *  2. 重打包：替换清单、剔除旧 V1 签名条目，其余条目原样搬运并保持压缩方式
 *  3. **是否签名完全交给设置里的「打包时签名」开关**（[OutputConfig.isSignEnabled]）：
 *     关闭 = 输出未签名 APK（交给用户自行签名）；打开 = 用内置密钥签名后输出
 *
 * 与主流程共用同一套输出目录与「保存的 APK」记录，产物可直接在应用内看到。
 */
object ApkVersionService {

    private const val MANIFEST = "AndroidManifest.xml"

    /** 版本号读取结果。任一字段可能为 null（清单里没写该属性）。 */
    data class VersionInfo(val versionName: String?, val versionCode: String?)

    /** 快速编辑结果。 */
    data class EditResult(
        val outputName: String,
        val outputPath: String,
        val signed: Boolean,
    )

    // ---------------- 读取 ----------------

    /** 读取 APK 的 versionName / versionCode。 */
    suspend fun readVersion(apk: File): VersionInfo = withContext(Dispatchers.IO) {
        val manifest = extractEntry(apk, MANIFEST)
        val v = AxmlVersion.read(manifest)
        VersionInfo(versionName = v[0], versionCode = v[1])
    }

    // ---------------- 快速编辑 ----------------

    /**
     * 修改版本号并输出新 APK。
     *
     * @param newName 新的 versionName；null 表示不改（沿用原值）
     * @param newCode 新的 versionCode；null 表示不改（沿用原值）
     */
    suspend fun quickEdit(
        context: Context,
        apk: File,
        newName: String?,
        newCode: Int?,
    ): EditResult = withContext(Dispatchers.IO) {
        require(newName != null || newCode != null) { "没有需要修改的版本信息" }

        val manifest = extractEntry(apk, MANIFEST)
        val patched = AxmlVersion.patch(manifest, newName, newCode)

        val workDir = File(context.cacheDir, "quickedit").apply { mkdirs() }
        val unsigned = File(workDir, "unsigned.apk")
        repack(apk, unsigned, MANIFEST, patched)

        val base = apk.name.removeSuffix(".apk").ifBlank { "output" }

        // 签名与否由设置决定，与主流程保持同一开关、同一密钥
        val signed = OutputConfig.isSignEnabled()
        val outFile: File = if (signed) {
            val (key, cert) = loadOrCreateSigningKey(context)
            val signedFile = File(workDir, "signed.apk")
            val signerConfig = ApkSigner.SignerConfig.Builder(
                "CN=ApkEditorMiuix", key, listOf(cert),
            ).build()
            ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(unsigned)
                .setOutputApk(signedFile)
                .setMinSdkVersion(35)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .build()
                .sign()
            signedFile
        } else {
            unsigned
        }

        val versionTag = newName ?: newCode?.toString() ?: "new"
        val finalName = if (signed) "${base}-v$versionTag-signed.apk" else "${base}-v$versionTag-unsigned.apk"
        val outputPath = writeToOutput(context, outFile, finalName)
        SavedApkStore.add(outputPath, finalName, outFile.length())
        EditResult(outputName = finalName, outputPath = outputPath, signed = signed)
    }

    // ---------------- ZIP 读写 ----------------

    private fun extractEntry(zip: File, name: String): ByteArray {
        ZipFile(zip).use { zf ->
            val e = zf.getEntry(name) ?: throw Exception("APK 中缺少条目: $name")
            return zf.getInputStream(e).use { it.readBytes() }
        }
    }

    /**
     * 重打包：替换 [replaceName] 条目、剔除旧 V1 签名条目、其余条目原样搬运。
     *
     * 不压缩条目（STORED）做对齐：lib 下的 .so 按 16KB 页对齐（同时满足 4K/16K 页设备），
     * 其余 STORED 条目 4 字节对齐（targetSdk 30+ 的 resources.arsc 硬要求）。
     * 输出不含任何签名块（旧 V2/V3 块随重写自然消失），需要签名时再由 apksig 处理。
     */
    private fun repack(src: File, dst: File, replaceName: String, replaceBytes: ByteArray) {
        FileOutputStream(dst).use { fos ->
            // 自己数流过的字节：ZipOutputStream 不暴露输出偏移，而对齐必须知道本地头部位置
            val counter = ByteCounterStream(fos)
            ZipOutputStream(BufferedOutputStream(counter, 1 shl 16)).use { zos ->
                ZipFile(src).use { zf ->
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        val name = e.name
                        if (isV1SignatureEntry(name)) continue

                        val isStored = e.method == ZipEntry.STORED
                        val nz = ZipEntry(name)
                        // 刻意不设 time：一旦设置，JDK 会自动追加 UT(0x5455) 扩展时间戳字段，
                        // 本地头部长度超出预期，下面的对齐会算错。
                        nz.method = if (isStored) ZipEntry.STORED else ZipEntry.DEFLATED

                        var bytes: ByteArray? = if (name == replaceName) replaceBytes else null
                        if (bytes != null) {
                            nz.size = bytes.size.toLong()
                            nz.crc = crc32Of(bytes)
                        } else if (e.size >= 0 && e.crc >= 0) {
                            // 未修改条目：沿用原 zip 元数据，边读边写，不把大资源读进内存
                            nz.size = e.size
                            nz.crc = e.crc
                        } else {
                            bytes = zf.getInputStream(e).use { it.readBytes() }
                            nz.size = bytes.size.toLong()
                            nz.crc = crc32Of(bytes)
                        }
                        if (isStored) nz.compressedSize = nz.size

                        val align = if (isStored) alignmentOf(name) else 1
                        if (align > 1) {
                            val extra = alignmentExtra(
                                counter.count,
                                name.toByteArray(Charsets.UTF_8).size,
                                align,
                            )
                            if (extra.isNotEmpty()) nz.extra = extra
                        }

                        zos.putNextEntry(nz)
                        if (bytes != null) zos.write(bytes!!)
                        else zf.getInputStream(e).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    /** 旧 V1 签名条目：重打包时必须剔除（否则会残留失效签名导致安装失败）。 */
    private fun isV1SignatureEntry(name: String): Boolean {
        val u = name.uppercase()
        if (!u.startsWith("META-INF/")) return false
        val f = u.substring(9)
        return f == "MANIFEST.MF" || f.endsWith(".SF") || f.endsWith(".RSA") ||
            f.endsWith(".DSA") || f.endsWith(".EC")
    }

    private fun alignmentOf(name: String): Int =
        if (name.startsWith("lib/") && name.endsWith(".so")) 16384 else 4

    /**
     * 算出使「数据起始偏移」满足 [align] 的 extra 字段。
     *
     * 数据偏移 = 本地头部起点 + 30（固定头长）+ 名字长度 + extra 长度，
     * 反解出 extra 长度即可。extra 内部沿用 Android 对齐惯例的 0xD935 ID，载荷补 0。
     */
    private fun alignmentExtra(headerStart: Long, nameLen: Int, align: Int): ByteArray {
        val base = headerStart + 30L + nameLen
        var pad = ((align - (base % align)) % align).toInt()
        if (pad == 0) return ByteArray(0)
        // extra 字段本身占 4 字节头，且总长需为偶数
        if (pad < 4) pad += align
        if (pad % 2 != 0) pad += align
        val extra = ByteArray(pad)
        extra[0] = 0x35
        extra[1] = 0xD9.toByte()
        val payload = pad - 4
        extra[2] = (payload and 0xFF).toByte()
        extra[3] = ((payload shr 8) and 0xFF).toByte()
        return extra
    }

    private fun crc32Of(data: ByteArray): Long {
        val c = CRC32()
        c.update(data)
        return c.value
    }

    // ---------------- 输出目录（与主流程一致的三级回退） ----------------

    /** 公共目录(有全部文件权限) → SAF 目录(失败回退) → 默认私有目录 */
    private fun writeToOutput(context: Context, file: File, name: String): String {
        if (OutputConfig.hasAllFilesAccess(context)) {
            val out = runCatching {
                val f = File(OutputConfig.publicOutputDir(context), name)
                file.copyTo(f, overwrite = true)
                f.absolutePath
            }.getOrNull()
            if (out != null) return out
        }
        val treeUri = OutputConfig.getTreeUri()
        if (treeUri != null) {
            val written = runCatching {
                val tree = Uri.parse(treeUri)
                val doc = android.provider.DocumentsContract.createDocument(
                    context.contentResolver, tree,
                    "application/vnd.android.package-archive", name,
                ) ?: throw IllegalStateException("无法在输出目录创建文件")
                context.contentResolver.openOutputStream(doc)?.use { os ->
                    file.inputStream().use { it.copyTo(os) }
                } ?: throw IllegalStateException("无法写入输出目录")
                doc.toString()
            }.getOrNull()
            if (written != null) return written
        }
        val out = File(OutputConfig.defaultOutputDir(context), name)
        file.copyTo(out, overwrite = true)
        return out.absolutePath
    }

    // ---------------- 签名密钥（与主流程同一个 keystore） ----------------

    private val KS_PASS = charArrayOf('a', 'p', 'k', 'e', 'd', 'i', 't', 'o', 'r')
    private const val KS_ALIAS = "apkeditor"

    private fun loadOrCreateSigningKey(context: Context): Pair<PrivateKey, X509Certificate> {
        val ksFile = File(context.filesDir, "signing.keystore")
        if (ksFile.exists()) {
            // 密钥库可能因上次写入被中断而损坏（PKCS12 只写了一半），必须容错
            val existing = runCatching {
                val ks = KeyStore.getInstance("PKCS12")
                ksFile.inputStream().use { ks.load(it, KS_PASS) }
                val key = ks.getKey(KS_ALIAS, KS_PASS) as PrivateKey
                val cert = ks.getCertificate(KS_ALIAS) as X509Certificate
                key to cert
            }.getOrNull()
            if (existing != null) return existing
            runCatching { ksFile.delete() }
        }
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        val kpg = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val kp = kpg.generateKeyPair()
        val now = java.util.Date()
        val validity = 3650L * 24 * 3600
        val name = org.bouncycastle.asn1.x500.X500Name("CN=ApkEditorMiuix, O=ApkEditorMiuix")
        val builder = org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
            name,
            java.math.BigInteger.valueOf(now.time),
            now,
            java.util.Date(now.time + validity * 1000L),
            name,
            kp.public,
        )
        val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA")
            .build(kp.private)
        val cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
            .getCertificate(builder.build(signer))
        ks.setKeyEntry(KS_ALIAS, kp.private, KS_PASS, arrayOf(cert))
        ksFile.outputStream().use { ks.store(it, KS_PASS) }
        return kp.private to cert
    }
}

/** 计数输出流：ZipOutputStream 不暴露输出偏移，对齐计算需要知道自己写了多少字节。 */
private class ByteCounterStream(out: OutputStream) : FilterOutputStream(out) {
    var count: Long = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }
}
