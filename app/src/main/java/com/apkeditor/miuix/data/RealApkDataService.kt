package com.apkeditor.miuix.data

import android.content.Context
import android.net.Uri
import com.apkeditor.miuix.SavedApkStore
import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkModule
import com.reandroid.apk.xmlencoder.XMLEncodeSource
import com.reandroid.arsc.model.ResourceEntry
import com.reandroid.xml.XMLFactory
import com.reandroid.xml.source.XMLFileParserSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.DexFileFactory
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * 真实反编译引擎实现。
 *
 * 底层：
 *  - ARSCLib：直接解析/编辑二进制 resources.arsc 与二进制 XML（弃用 apktool+aapt2，
 *    规避 Android17 上 arsc 反编译后回编译崩溃的问题）
 *  - smali/baksmali 2.5.2：DEX ↔ smali 反汇编/汇编（整 dex 级）
 *  - apksig：APK v1+v2 签名
 *
 * 重打包策略（关键）：
 *  不再用 ARSCLib 全量 writeApk 重写 APK（那会连未修改的 arsc/dex 一起重编码，存在损坏风险）。
 *  改为 **zip 拷贝 + 只替换修改过的文件**：遍历原始 APK 所有条目，仅把
 *  被修改过的 dex / xml / arsc 用新字节替换，其余条目逐字节原样保留。
 *  这样「未修改内容直接回编译」= 原 APK 逐字节拷贝 + 重新签名，产物必然可用。
 *
 * 线程模型：全部引擎操作用 [Dispatchers.IO]；同一 [ApkModule] 的读写用 [Mutex] 串行保护。
 */
class RealApkDataService(private val context: Context) : ApkDataService {

    private val mutex = Mutex()
    private var module: ApkModule? = null
    private var apkFileName: String = "input.apk"
    /** 原始 APK 文件路径（zip 拷贝重打包的数据源） */
    private var rawApkFile: File? = null

    // ---------- 修改记录（zip 拷贝 + 只替换修改文件 的核心） ----------
    /** 被修改过的 dex：dexName → 汇编产物文件 */
    private val modifiedDex = HashMap<String, File>()
    /** 被修改过的 xml：路径 → 编码后二进制字节 */
    private val modifiedXml = HashMap<String, ByteArray>()
    /** resources.arsc 是否被修改 */
    private var modifiedArsc = false

    /** smali 反编译缓存：返回时不重复反编译（首次反编译后复用） */
    private val smaliCache = HashMap<String, List<String>>()

    // ---------- 基础 ----------

    private fun cacheDir() = context.cacheDir

    private fun workDir(): File = File(cacheDir(), "work")

    /** 在 IO 线程 + 锁内操作已打开的模块 */
    private suspend fun <T> locked(block: (ApkModule) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val m = module ?: throw IllegalStateException("尚未打开 APK")
            block(m)
        }
    }

    private fun copyUriToCache(uri: String, out: File) {
        out.parentFile?.mkdirs()
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { ins ->
            FileOutputStream(out).use { fos -> ins.copyTo(fos) }
        } ?: throw IllegalStateException("无法读取所选文件")
    }

    // ---------- APK 加载 ----------

    override suspend fun loadApk(uri: String): Result<ApkInfo> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                val input = File(cacheDir(), "apk/input.apk")
                copyUriToCache(uri, input)
                val m = ApkModule.loadApkFile(input)
                module = m
                rawApkFile = input
                apkFileName = input.name
                smaliCache.clear()
                modifiedDex.clear()
                modifiedXml.clear()
                modifiedArsc = false

                val manifest = m.getAndroidManifestBlock()
                val label = runCatching { manifest.getApplicationLabelString() }.getOrNull()
                    ?: manifest.packageName
                val versionCode = runCatching { manifest.versionCode }.getOrNull() ?: 0
                val minSdk = runCatching { manifest.minSdkVersion }.getOrNull() ?: 35
                val targetSdk = runCatching { manifest.targetSdkVersion }.getOrNull() ?: 37
                val permissions = runCatching { manifest.usesPermissions }.getOrElse { emptyList() }
                val mainActivity = runCatching { manifest.mainActivityClassName }.getOrNull() ?: ""
                val dexNames = listDexNamesFromZip(input)
                val resourceCount = runCatching { countResources(m) }.getOrElse { 0 }

                ApkInfo(
                    fileName = "input.apk",
                    label = label,
                    packageName = manifest.packageName,
                    versionName = runCatching { manifest.versionName }.getOrNull() ?: "?",
                    versionCode = versionCode,
                    minSdk = minSdk,
                    targetSdk = targetSdk,
                    permissions = permissions,
                    mainActivity = mainActivity,
                    dexNames = dexNames,
                    fileSize = input.length().toDisplaySize(),
                    resourceCount = resourceCount,
                )
            }
        }
    }

    private fun countResources(m: ApkModule): Int {
        var count = 0
        m.tableBlock.resources.forEach { count++ }
        return count
    }

    /** 用 zip 直接列出 classes*.dex（快，不解析） */
    private fun listDexNamesFromZip(file: File): List<String> {
        ZipFile(file).use { zf ->
            return zf.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("classes") && it.endsWith(".dex") }
                .sorted()
                .toList()
        }
    }

    private fun Long.toDisplaySize(): String {
        val kb = this / 1024.0
        return when {
            this < 1024 -> "$this B"
            kb < 1024 -> String.format("%.1f KB", kb)
            else -> String.format("%.1f MB", kb / 1024.0)
        }
    }

    // ---------- APK 内容预览（zip 直接解压，快） ----------

    override suspend fun listApkContents(): Result<List<ApkEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val src = rawApkFile ?: throw IllegalStateException("尚未打开 APK")
            ZipFile(src).use { zf ->
                val list = mutableListOf<ApkEntry>()
                zf.entries().asSequence().forEach { e ->
                    list.add(ApkEntry(path = e.name, size = e.size))
                }
                list
            }
        }
    }

    // ---------- DEX ----------

    override suspend fun listDexFiles(): Result<List<DexEntry>> = runCatching {
        rawApkFile?.let { f ->
            ZipFile(f).use { zf ->
                zf.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("classes") && it.endsWith(".dex") }
                    .map { DexEntry(it, "") }
                    .toList()
            }
        } ?: locked { m ->
            m.listDexFiles().map { DexEntry(it.alias, "") }
        }
    }

    private fun dexWorkDir(dexName: String): File {
        val d = File(workDir(), "$apkFileName/$dexName")
        d.mkdirs()
        return d
    }

    /** smali 目录名：classes.dex → smali，classesN.dex → smali_classesN（MT/apktool 风格，与 dex 一一对应） */
    private fun smaliDirName(dexName: String): String {
        val n = dexName.removePrefix("classes").removeSuffix(".dex")
        return if (n.isEmpty()) "smali" else "smali_classes$n"
    }

    /** 反汇编一个或多个 dex 到 work 目录（带缓存，多 dex 并行 baksmali），返回 各dex → smali相对路径列表 */
    private suspend fun decompileAll(dexNames: List<String>): Map<String, List<String>> =
        withContext(Dispatchers.IO) {
            // 1) 锁内：只把尚未缓存的 dex 字节解到临时文件（避免 module 被长时间占用）
            mutex.withLock {
                val m = module ?: throw IllegalStateException("尚未打开 APK")
                val srcMap = m.listDexFiles().associateBy { it.alias }
                dexNames.forEach { dex ->
                    val dir = dexWorkDir(dex)
                    val dexFile = File(dir, dex)
                    if (!dexFile.exists()) {
                        val src = srcMap[dex] ?: throw IllegalStateException("APK 中不存在 $dex")
                        src.openStream().use { ins ->
                            FileOutputStream(dexFile).use { it.write(ins.readBytes()) }
                        }
                    }
                }
            }
            // 2) 锁外：并行 baksmali 反汇编
            val result = kotlinx.coroutines.coroutineScope {
                dexNames.map { dex ->
                    async {
                        smaliCache[dex]?.let { return@async dex to it }
                        val dir = dexWorkDir(dex)
                        val smaliDir = File(dir, smaliDirName(dex))
                        if (!smaliDir.exists()) {
                            smaliDir.mkdirs()
                            val dexFileObj = DexFileFactory.loadDexFile(
                                File(dir, dex), Opcodes.getDefault(),
                            )
                            Baksmali.disassembleDexFile(dexFileObj, smaliDir, 0, BaksmaliOptions())
                        }
                        val files = collectSmali(smaliDir)
                        smaliCache[dex] = files
                        dex to files
                    }
                }.awaitAll().toMap()
            }
            result
        }

    private fun collectSmali(smaliDir: File): List<String> {
        val files = mutableListOf<String>()
        val prefix = smaliDir.name + "/"
        fun walk(f: File, p: String) {
            f.listFiles()?.forEach { child ->
                if (child.isDirectory) walk(child, "$p${child.name}/")
                else files.add("$p${child.name}")
            }
        }
        walk(smaliDir, prefix)
        return files.sorted()
    }

    override suspend fun listSmaliFiles(dexName: String): Result<List<String>> = runCatching {
        smaliCache[dexName]?.let { return@runCatching it }
        val r = decompileAll(listOf(dexName))[dexName] ?: emptyList()
        smaliCache[dexName] = r
        r
    }

    override suspend fun readSmaliFile(dexName: String, filePath: String): Result<String> = runCatching {
        File(workDir(), "$apkFileName/$dexName/$filePath").readText()
    }

    override suspend fun saveSmaliFile(dexName: String, filePath: String, content: String): Result<Unit> =
        runCatching {
            File(workDir(), "$apkFileName/$dexName/$filePath").apply {
                parentFile?.mkdirs()
                writeText(content)
            }
        }

    override suspend fun assembleDex(dexName: String): Result<Unit> = runCatching {
        val dir = dexWorkDir(dexName)
        val smaliDir = File(dir, smaliDirName(dexName))
        if (!smaliDir.exists()) throw IllegalStateException("请先反汇编 $dexName")
        val outDex = File(dir, "out-$dexName")
        val opts = SmaliOptions().apply { outputDexFile = outDex.absolutePath }
        Smali.assemble(opts, smaliDir.absolutePath)
        if (!outDex.exists()) throw IllegalStateException("smali 汇编失败：未生成 dex")
        // 记录修改：重打包时用汇编产物替换原 dex（不再 m.add 到模块，避免模块内源混乱）
        modifiedDex[dexName] = outDex
    }

    // ---------- ARSC 资源 ----------

    override suspend fun listResourceTypes(): Result<List<ResourceTypeInfo>> = runCatching {
        locked { m ->
            val map = LinkedHashMap<String, Int>()
            m.tableBlock.resources.forEach { r ->
                val type = runCatching { r.type }.getOrNull() ?: "?"
                map[type] = (map[type] ?: 0) + 1
            }
            map.map { (k, v) -> ResourceTypeInfo(k, v) }
        }
    }

    override suspend fun listResources(type: String): Result<List<ResourceEntryInfo>> = runCatching {
        locked { m ->
            val list = mutableListOf<ResourceEntryInfo>()
            m.tableBlock.resources.forEach { r ->
                if ((runCatching { r.type }.getOrNull() ?: "?") != type) return@forEach
                val entry = r
                list.add(
                    ResourceEntryInfo(
                        id = entry.resourceId,
                        hexId = entry.hexId,
                        name = runCatching { entry.name }.getOrNull() ?: "",
                        type = type,
                        value = firstValue(entry),
                        configs = collectConfigs(entry),
                    )
                )
            }
            list
        }
    }

    private fun firstValue(entry: ResourceEntry): String {
        runCatching {
            entry.iterator().forEach { e ->
                val v = runCatching { e.valueAsString }.getOrNull()
                if (!v.isNullOrBlank()) return v
            }
        }
        return ""
    }

    private fun collectConfigs(entry: ResourceEntry): List<String> {
        val list = mutableListOf<String>()
        runCatching {
            entry.iterator().forEach { e ->
                val cfg = runCatching { e.resConfig?.qualifiers }.getOrNull() ?: "default"
                list.add(if (cfg.isBlank()) "default" else cfg)
            }
        }
        return list.distinct()
    }

    override suspend fun saveResourceValue(id: Int, qualifiers: String?, newValue: String): Result<Unit> =
        runCatching {
            locked { m ->
                m.tableBlock.resources.forEach { r ->
                    if (r.resourceId != id) return@forEach
                    r.iterator().forEach { e ->
                        val cfg = runCatching { e.resConfig?.qualifiers }.getOrNull() ?: "default"
                        val match = qualifiers.isNullOrBlank() || cfg == qualifiers || cfg == "default"
                        if (match) {
                            e.setValueAsString(newValue)
                        }
                    }
                }
                modifiedArsc = true
            }
        }

    override suspend fun renameResource(id: Int, newName: String): Result<Unit> = runCatching {
        locked { m ->
            m.tableBlock.resources.forEach { r ->
                if (r.resourceId == id) {
                    r.setName(newName)
                }
            }
            modifiedArsc = true
        }
    }

    // ---------- XML ----------

    override suspend fun listXmlFiles(): Result<List<XmlFileInfo>> = runCatching {
        val src = rawApkFile ?: throw IllegalStateException("尚未打开 APK")
        ZipFile(src).use { zf ->
            zf.entries().asSequence()
                .map { it.name }
                .filter { it.endsWith(".xml") }
                .sorted()
                .map { XmlFileInfo(path = it, size = "") }
                .toList()
        }
    }

    override suspend fun readXmlFile(path: String): Result<String> = runCatching {
        locked { m ->
            val doc = m.decodeXMLFile(path)
            val out = File(workDir(), "$apkFileName/tmp/${path.substringAfterLast('/')}.xml.txt")
            out.parentFile?.mkdirs()
            val serializer = XMLFactory.newSerializer(out)
            doc.serialize(serializer)
            serializer.flush()
            out.readText()
        }
    }

    override suspend fun saveXmlFile(path: String, content: String): Result<Unit> = runCatching {
        locked { m ->
            val tmp = File(workDir(), "$apkFileName/tmp/${path.substringAfterLast('/')}.xml")
            tmp.parentFile?.mkdirs()
            tmp.writeText(content)
            val pkg = m.tableBlock.packages.asSequence().first()
            val source = XMLEncodeSource(pkg, XMLFileParserSource(path, tmp))
            // 记录编码后的二进制字节：重打包时替换原 xml
            modifiedXml[path] = source.getBytes()
        }
    }

    // ---------- 打包签名（zip 拷贝 + 只替换修改文件 + 签名） ----------

    override suspend fun buildAndSign(): Result<BuildResult> = withContext(Dispatchers.IO) {
        runCatching {
            var outputPath: String
            var outputName: String
            val signedFile: File
            mutex.withLock {
                val m = module ?: throw IllegalStateException("尚未打开 APK")
                val src = rawApkFile ?: throw IllegalStateException("尚未打开 APK")
                val unsigned = File(cacheDir(), "out/unsigned.apk")
                unsigned.parentFile?.mkdirs()
                writeUnsignedApk(src, unsigned, m)
                val (key, cert) = loadOrCreateSigningKey()
                signedFile = File(cacheDir(), "out/signed.apk")
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
                // 统一输出到配置目录
                val base = apkFileName.removeSuffix(".apk").ifBlank { "output" }
                outputName = "${base}-signed.apk"
                outputPath = writeToOutput(signedFile, outputName)
            }
            // 记录到"保存的 APK"
            SavedApkStore.add(outputPath, outputName, signedFile.length())
            BuildResult(outputName = outputName, signed = true, outputPath = outputPath)
        }
    }

    /** 重打包：遍历原始 APK，仅替换被修改的 dex/xml/arsc，其余条目原样拷贝 */
    private fun writeUnsignedApk(src: File, out: File, m: ApkModule) {
        // 若 arsc 被修改，预生成其二进制字节
        val arscBytes: ByteArray? = if (modifiedArsc) {
            val tmp = File(cacheDir(), "out/arsc.bin")
            m.tableBlock.writeBytes(tmp)
            tmp.readBytes()
        } else null

        ZipOutputStream(FileOutputStream(out)).use { zos ->
            ZipFile(src).use { zf ->
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val name = e.name
                    val nz = ZipEntry(name)
                    nz.time = e.time
                    nz.method = e.method
                    zos.putNextEntry(nz)
                    val bytes: ByteArray? = when {
                        modifiedDex.containsKey(name) -> modifiedDex[name]!!.readBytes()
                        modifiedXml.containsKey(name) -> modifiedXml[name]
                        name == "resources.arsc" && modifiedArsc -> arscBytes
                        else -> null
                    }
                    if (bytes != null) zos.write(bytes)
                    else zf.getInputStream(e).use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    /** 写入统一输出目录：公共目录(有全部文件权限) → SAF(失败回退) → 默认私有目录 */
    private fun writeToOutput(signed: File, name: String): String {
        // 1) 公共目录：已授予"所有文件访问"，直接写公共存储
        if (OutputConfig.hasAllFilesAccess(context)) {
            return try {
                val out = File(OutputConfig.publicOutputDir(context), name)
                signed.copyTo(out, overwrite = true)
                out.absolutePath
            } catch (e: Exception) {
                // 写公共目录失败 → 回退默认
                val out = File(OutputConfig.defaultOutputDir(context), name)
                signed.copyTo(out, overwrite = true)
                out.absolutePath
            }
        }
        // 2) SAF 目录（失败回退默认，避免"未知目录"卡死）
        val treeUri = OutputConfig.getTreeUri()
        if (treeUri != null) {
            try {
                val tree = Uri.parse(treeUri)
                val doc = android.provider.DocumentsContract.createDocument(
                    context.contentResolver, tree, "application/vnd.android.package-archive", name,
                ) ?: throw IllegalStateException("无法在输出目录创建文件")
                context.contentResolver.openOutputStream(doc)?.use { os ->
                    signed.inputStream().use { it.copyTo(os) }
                } ?: throw IllegalStateException("无法写入输出目录")
                return doc.toString()
            } catch (e: Exception) {
                // SAF 目录失效，回退默认目录
            }
        }
        // 3) 默认私有目录
        val out = File(OutputConfig.defaultOutputDir(context), name)
        signed.copyTo(out, overwrite = true)
        return out.absolutePath
    }

    // ---------- 签名密钥（首次生成自签名证书，持久化到私有目录） ----------

    private fun loadOrCreateSigningKey(): Pair<PrivateKey, X509Certificate> {
        val ksFile = File(context.filesDir, "signing.keystore")
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        if (ksFile.exists()) {
            ksFile.inputStream().use { ks.load(it, KS_PASS) }
            runCatching {
                val key = ks.getKey(KS_ALIAS, KS_PASS) as PrivateKey
                val cert = ks.getCertificate(KS_ALIAS) as X509Certificate
                return key to cert
            }
        }
        // 生成新的 RSA 密钥 + 自签名证书（BouncyCastle，Android 可用）
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
        val signer = org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withRSA").build(kp.private)
        val cert = org.bouncycastle.cert.jcajce.JcaX509CertificateConverter().getCertificate(builder.build(signer))
        ks.setKeyEntry(KS_ALIAS, kp.private, KS_PASS, arrayOf(cert))
        ksFile.outputStream().use { ks.store(it, KS_PASS) }
        return kp.private to cert
    }

    private companion object {
        val KS_PASS = charArrayOf('a', 'p', 'k', 'e', 'd', 'i', 't', 'o', 'r')
        const val KS_ALIAS = "apkeditor"
    }
}
