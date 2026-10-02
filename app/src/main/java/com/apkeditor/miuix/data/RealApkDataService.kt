package com.apkeditor.miuix.data

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.TypedValue
import org.xmlpull.v1.XmlPullParser
import android.net.Uri
import com.apkeditor.miuix.SavedApkStore
import com.android.apksig.ApkSigner
import com.reandroid.apk.ApkModule
import com.reandroid.apk.xmlencoder.XMLEncodeSource
import com.reandroid.arsc.coder.ComplexUtil
import com.reandroid.arsc.model.ResourceEntry
import com.reandroid.arsc.value.Entry
import com.reandroid.arsc.value.ValueType
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
 *  写回时做**进程内 zipalign**（Android 上没有 zipalign 可执行文件）：
 *  不压缩条目要落在 4 字节边界，lib 下不压缩的 .so 要落在 4096 页边界。
 *  详见 [writeUnsignedApk] 与 [alignmentExtra]。
 *
 * 线程模型：全部引擎操作用 [Dispatchers.IO]；同一 [ApkModule] 的读写用 [Mutex] 串行保护。
 */
class RealApkDataService(private val context: Context) : ApkDataService {

    private val mutex = Mutex(); private val cacheMgr = ApkCacheManager(context); private var cacheEntry: ApkCacheEntry? = null
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
    /** 每个 dex 的目录树节点缓存：换一组 dexNames 时不必重新建树 */
    private val dexNodeCache = HashMap<String, SmaliTreeNode>()
    /** 合并后的目录树缓存：按 dexNames 组合缓存，退出再进来直接复用 */
    private val smaliMergedCache = HashMap<String, List<SmaliTreeNode>>()

    /** 当前 APK 的 minSdk（用于 baksmali 设置正确的 Opcodes，同 NP 管理器） */
    private var currentMinSdk: Int = 35

    /** 已加载 APK 的 uri 与其解析结果：同一 APK 从编辑页返回信息页时复用，避免重置修改记录 */
    private var loadedUri: String? = null
    private var cachedInfo: ApkInfo? = null

    init {
        activeInstance = this
        ApkCacheManager.registerMemoryCleaner {
            runCatching { activeInstance?.clearMemoryCache() }
        }
    }

    /** 清理内存中的 smali 缓存与目录树缓存（供清除缓存调用） */
    private fun clearMemoryCache() {
        smaliCache.clear()
        clearTreeCaches()
    }

    /** 清理目录树相关缓存（文件增删改名、换 APK 后调用） */
    private fun clearTreeCaches() {
        dexNodeCache.clear()
        smaliMergedCache.clear()
    }

    // ---------- 基础 ----------

    private fun cacheDir() = context.cacheDir

    private fun workDir(): File { val e = cacheEntry; return if (e != null) cacheMgr.workDir(e) else File(cacheDir(), "work").apply { mkdirs() } }

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
                // 同一个 APK 再次打开 —— 最典型的就是「从反编译编辑页返回 APK 信息页」，
                // 而 ApkInfoPage 每次进入都会调 loadApk。此时必须直接复用已加载的 ApkModule
                // 与 modifiedDex/modifiedXml/modifiedArsc：一旦往下走完整加载流程，
                // module 会被换成从原始 APK 重新解析的新对象，旧对象上已做过的 arsc/xml 修改
                // 会随之消失，待应用的 dex/xml 记录也会被 clear ——
                // 表现就是"刚改完返回上一页，修改无效了"。
                if (uri == loadedUri && module != null) {
                    cachedInfo?.let { return@withLock it }
                }

                val tmpInput = File(cacheDir(), "apk/tmp.apk")
                copyUriToCache(uri, tmpInput)
                val entry = cacheMgr.prepare(tmpInput)
                val input = entry.inputApk
                val hashChanged = cacheEntry?.hash != entry.hash
                cacheEntry = entry
                val m = ApkModule.loadApkFile(input)
                module = m
                rawApkFile = input
                apkFileName = Uri.parse(uri).lastPathSegment ?: "input.apk"
                if (hashChanged) smaliCache.clear()
                if (hashChanged) clearTreeCaches()
                // 走到这里说明换了一个 APK（或首次打开），上一个 APK 的待应用修改已无意义
                modifiedDex.clear()
                modifiedXml.clear()
                modifiedArsc = false
                loadedUri = uri

                val manifest = m.getAndroidManifestBlock()
                val label = runCatching { manifest.getApplicationLabelString() }.getOrNull()
                    ?: manifest.packageName
                val versionCode = runCatching { manifest.versionCode }.getOrNull() ?: 0
                val minSdk = runCatching { manifest.minSdkVersion }.getOrNull() ?: 35
                val targetSdk = runCatching { manifest.targetSdkVersion }.getOrNull() ?: 37
                currentMinSdk = minSdk
                val permissions = runCatching { manifest.usesPermissions }.getOrElse { emptyList() }
                val mainActivity = runCatching { manifest.mainActivityClassName }.getOrNull() ?: ""
                val dexNames = listDexNamesFromZip(input)
                val resourceCount = runCatching { countResources(m) }.getOrElse { 0 }

                val info = ApkInfo(
                    fileName = apkFileName,
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
                cachedInfo = info
                info
            }
        }
    }

    /**
     * 抽取 APK 真实应用图标：loadApk 时原 APK 已被拷进缓存（cacheEntry.inputApk），
     * 直接让 PackageManager 从该文件解析图标（未安装的 APK 也可用）。
     */
    override suspend fun loadApkIcon(uri: String): Result<android.graphics.drawable.Drawable?> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (uri != loadedUri) return@runCatching null
                val f = cacheEntry?.inputApk ?: return@runCatching null
                if (!f.exists()) return@runCatching null
                context.packageManager.getApplicationArchiveIcon(f.absolutePath)
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
        val d = File(workDir(), dexName)
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
            // 2) 锁外：逐 dex 反汇编（单 dex 内多线程用满核心，避免双层并行导致线程爆炸）
            val threadCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            val result = HashMap<String, List<String>>()
            for (dex in dexNames) {
                smaliCache[dex]?.let { result[dex] = it; continue }
                val dir = dexWorkDir(dex)
                val smaliDir = File(dir, smaliDirName(dex))
                if (!smaliDir.exists()) {
                    smaliDir.mkdirs()
                    val dexFileObj = DexFileFactory.loadDexFile(
                        File(dir, dex), Opcodes.getDefault(),
                    )
                    val options = BaksmaliOptions().apply {
                        apiLevel = currentMinSdk
                    }
                    Baksmali.disassembleDexFile(dexFileObj, smaliDir, threadCount, options)
                }
                val files = collectSmali(smaliDir)
                smaliCache[dex] = files
                result[dex] = files
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

    override suspend fun listSmaliMergedTree(
        dexNames: List<String>,
        onProgress: ((Int, Int) -> Unit)?,
    ): Result<List<SmaliTreeNode>> = runCatching {
        val key = dexNames.joinToString(",")
        smaliMergedCache[key]?.let { return@runCatching it }
        val roots = mutableListOf<SmaliTreeNode>()
        dexNames.forEachIndexed { index, dex ->
            onProgress?.invoke(index, dexNames.size)
            val cached = dexNodeCache[dex]
            roots.add(
                cached ?: buildDexNode(dex, listSmaliFiles(dex).getOrThrow())
                    .also { dexNodeCache[dex] = it }
            )
        }
        onProgress?.invoke(dexNames.size, dexNames.size)
        val merged = mergeTopNodes(roots.flatMap { it.children })
        smaliMergedCache[key] = merged
        merged
    }

    /** 建树中间态：先用 map 归并目录，最后一次性转成不可变节点 */
    private class TreeBuild(val name: String, val path: String, val isDir: Boolean, val dex: String) {
        val dirs = LinkedHashMap<String, TreeBuild>()
        val leaves = ArrayList<TreeBuild>()
    }

    /** 由 (dex, 文件相对路径) 构建一个 dex 顶层目录节点 */
    private fun buildDexNode(dex: String, files: List<String>): SmaliTreeNode {
        val rootName = if (dex == "classes.dex") "smali" else "smali_" + dex.removeSuffix(".dex")
        val root = TreeBuild(rootName, dex, true, dex)
        files.sorted().forEach { file ->
            // file 形如 smali/com/apkeditor/miuix/MainActivity.smali（含 smali 前缀）
            val parts = file.split("/").drop(1) // 去掉 smali 前缀
            if (parts.isEmpty()) return@forEach
            var cur = root
            var acc = ""
            parts.dropLast(1).forEach { seg ->
                acc = if (acc.isEmpty()) seg else "$acc/$seg"
                cur = cur.dirs.getOrPut(seg) { TreeBuild(seg, acc, true, dex) }
            }
            cur.leaves.add(TreeBuild(parts.last(), file, false, dex))
        }
        return toNode(root)
    }

    /**
     * 中间态转不可变节点。子节点按名字排序，与原先「按完整路径排序后依次插入」的展示顺序一致。
     *
     * 原先的 insertPath 每插一个节点都要 dir.copy(...) 并 children.indexOf(dir)，
     * 而 SmaliTreeNode 是 data class，indexOf 会递归比较整棵子树 ——
     * 几万个 smali 文件时是 O(n²) 级别开销（比 baksmali 本身还慢，
     * 表现为"退出再进来像又重新反编译了一遍"）。这里改成先归并再一次性转换。
     */
    private fun toNode(b: TreeBuild): SmaliTreeNode {
        val children = (b.leaves + b.dirs.values).sortedBy { it.name }.map { child ->
            if (child.isDir) toNode(child)
            else SmaliTreeNode(child.name, child.path, false, child.dex)
        }
        return SmaliTreeNode(b.name, b.path, b.isDir, b.dex, children)
    }

    /** 合并多个 dex 的顶层节点：同名目录递归合并 */
    private fun mergeTopNodes(nodes: List<SmaliTreeNode>): List<SmaliTreeNode> {
        val byName = LinkedHashMap<String, SmaliTreeNode>()
        nodes.forEach { node ->
            val existing = byName[node.name]
            when {
                existing == null -> byName[node.name] = node
                existing.isDir && node.isDir ->
                    byName[node.name] = existing.copy(
                        children = mergeTopNodes(existing.children + node.children)
                    )
                // 同名但非目录（两个 dex 有同名文件）：保留先出现的
            }
        }
        return byName.values.toList()
    }

    override suspend fun readSmaliFile(dexName: String, filePath: String): Result<String> = runCatching {
        val f = File(workDir(), "$dexName/$filePath")
        if (!f.exists()) error("smali 文件不存在：${f.relativeToOrNull(workDir()) ?: f.path}")
        f.readText()
    }

    override suspend fun saveSmaliFile(dexName: String, filePath: String, content: String): Result<Unit> =
        runCatching {
            File(workDir(), "$dexName/$filePath").apply {
                parentFile?.mkdirs()
                writeText(content)
            }
        }

    override suspend fun listSmaliClasses(dexNames: List<String>): Result<List<SmaliClassEntry>> = runCatching {
        val tree = listSmaliMergedTree(dexNames, null).getOrThrow()
        val out = mutableListOf<SmaliClassEntry>()
        fun walk(nodes: List<SmaliTreeNode>) {
            nodes.forEach { n ->
                if (n.isDir) walk(n.children)
                else out.add(SmaliClassEntry(n.dex, n.path, classNameOfPath(n.path)))
            }
        }
        walk(tree)
        out.sortedWith(compareBy({ it.className }, { it.dex }))
    }

    /** smali/com/x/Foo.smali → com.x.Foo（smali 前缀可能带 _classesN 后缀） */
    private fun classNameOfPath(path: String): String {
        val p = path.substringAfter("/", path)
        return p.removeSuffix(".smali").replace("/", ".")
    }

    override suspend fun readSmaliClassDetail(dexName: String, filePath: String): Result<SmaliClassDetail> =
        runCatching {
            val text = readSmaliFile(dexName, filePath).getOrThrow()
            SmaliParser.parse(text).detail
        }

    override suspend fun readSmaliMethod(dexName: String, filePath: String, methodIndex: Int): Result<String> =
        runCatching {
            val text = readSmaliFile(dexName, filePath).getOrThrow()
            val block = SmaliParser.parse(text).blocks.getOrNull(methodIndex)
                ?: error("方法不存在（文件可能已被修改）")
            text.split("\n").subList(block.start, block.end + 1).joinToString("\n")
        }

    override suspend fun saveSmaliMethod(
        dexName: String,
        filePath: String,
        methodIndex: Int,
        methodHeader: String,
        content: String,
    ): Result<Unit> = runCatching {
        val f = File(workDir(), "$dexName/$filePath")
        if (!f.exists()) error("smali 文件不存在：$filePath")
        val text = f.readText()
        val lines = text.split("\n").toMutableList()
        val block = SmaliParser.parse(text).blocks.getOrNull(methodIndex)
            ?: error("方法不存在（文件可能已被修改）")
        // 声明行校验：防止文件在别处被改动后错位覆盖
        if (lines[block.start].trim() != methodHeader) {
            error("方法位置已变化，请返回类详情页刷新后重试")
        }
        val newLines = content.trimEnd('\n').split("\n")
        lines.subList(block.start, block.end + 1).clear()
        lines.addAll(block.start, newLines)
        f.writeText(lines.joinToString("\n"))
    }

    override suspend fun renameSmaliFile(dexName: String, filePath: String, newClassName: String): Result<Unit> =
        runCatching {
            val oldFile = File(workDir(), "$dexName/$filePath")
            if (!oldFile.exists()) error("文件不存在：$filePath")
            // 包路径保持不变，只改类名
            val dir = oldFile.parentFile!!
            val newFile = File(dir, "$newClassName.smali")
            // 读内容，替换 .class 声明里的类名
            val oldContent = oldFile.readText()
            val oldClassName = filePath.substringAfterLast("/").removeSuffix(".smali")
            val newContent = oldContent.replace(
                ".class public L$oldClassName;",
                ".class public L$newClassName;"
            ).replace(
                ".class final L$oldClassName;",
                ".class final L$newClassName;"
            )
            newFile.writeText(newContent)
            oldFile.delete()
            // 清缓存让树重建
            smaliCache.remove(dexName)
            clearTreeCaches()
        }

    override suspend fun deleteSmaliFile(dexName: String, filePath: String): Result<Unit> = runCatching {
        val f = File(workDir(), "$dexName/$filePath")
        if (!f.exists()) error("文件不存在：$filePath")
        f.delete()
        smaliCache.remove(dexName)
        clearTreeCaches()
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

    override suspend fun discardModifications(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                modifiedDex.clear()
                modifiedXml.clear()
                modifiedArsc = false
                // 从原始 APK 重新解析模块，丢掉内存里已经应用的 arsc / xml 修改
                rawApkFile?.let { module = ApkModule.loadApkFile(it) }
                smaliCache.clear()
                clearTreeCaches()
                // 连同磁盘上的反编译产物一起丢掉，下次进入即从原始 APK 重新开始。
                // 末尾显式 Unit：withLock 块的返回值会决定外层 runCatching 的类型，
                // 不收敛的话结果是 Result<Boolean>，与接口声明的 Result<Unit> 不匹配。
                workDir().deleteRecursively()
                Unit
            }
        }
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
                list.add(buildResourceEntryInfo(r))
            }
            list
        }
    }

    override suspend fun searchResources(
        type: String?,
        keyword: String,
        maxResults: Int,
    ): Result<List<ResourceEntryInfo>> = runCatching {
        locked { m ->
            val kw = keyword.trim().lowercase()
            val list = mutableListOf<ResourceEntryInfo>()
            m.tableBlock.resources.forEach { r ->
                if (list.size >= maxResults) return@forEach
                val t = runCatching { r.type }.getOrNull() ?: "?"
                if (type != null && t != type) return@forEach
                if (kw.isNotEmpty()) {
                    val name = runCatching { r.name }.getOrNull() ?: ""
                    val entryInfo = buildResourceEntryInfo(r)
                    // 匹配资源名 或 资源值（array 类型匹配所有元素）
                    val valueText = entryInfo.variants.joinToString(" ") { it.displayValue }
                    if (!name.lowercase().contains(kw) && !valueText.lowercase().contains(kw)) {
                        return@forEach
                    }
                    list.add(entryInfo)
                } else {
                    list.add(buildResourceEntryInfo(r))
                }
            }
            list
        }
    }

    /** 把 ARSCLib 的 ResourceEntry 转成 UI 模型：收集全部 config 变体，按值类型正确解码 */
    private fun buildResourceEntryInfo(r: ResourceEntry): ResourceEntryInfo {
        val name = runCatching { r.name }.getOrNull() ?: ""
        val type = runCatching { r.type }.getOrNull() ?: "?"
        val variants = collectVariants(r)
        // 默认值：取 default 变体，否则取第一个非空变体
        val defaultVariant = variants.firstOrNull { it.qualifiers.isEmpty() }
            ?: variants.firstOrNull { it.displayValue.isNotEmpty() }
        val value = defaultVariant?.displayValue ?: ""
        return ResourceEntryInfo(
            id = r.resourceId,
            hexId = r.hexId,
            name = name,
            type = type,
            value = value,
            configs = variants.map { it.qualifiers }.distinct(),
            variants = variants,
        )
    }

    /**
     * 收集一个资源的全部配置变体（-L / -R / night / dpi / land 等）。
     * 每个变体按 [ValueType] 正确解码：
     *  - string → 原文
     *  - integer / hex → 数值字符串
     *  - bool → true/false
     *  - float → 浮点字符串
     *  - dimension → ComplexUtil.decodeComplex（支持负数，如 -330dp）
     *  - color → AndroidColor.toHexString（如 #FF223344）
     *  - reference → 递归解析引用（深度限制防循环），解析失败则输出 @type/name
     */
    private fun collectVariants(r: ResourceEntry): List<ResourceVariant> {
        val result = mutableListOf<ResourceVariant>()
        runCatching {
            r.iterator().forEach { e: Entry ->
                val cfg = runCatching { e.resConfig?.qualifiers }.getOrNull() ?: ""
                val qualifiers = if (cfg.isNullOrBlank()) "" else cfg
                val v = decodeEntryValue(r, e)
                result.add(
                    ResourceVariant(
                        qualifiers = qualifiers,
                        valueType = v.first,
                        displayValue = v.second,
                        rawData = runCatching { e.resValue?.data }.getOrNull() ?: 0,
                    )
                )
            }
        }
        return result
    }

    /** 解码单个 Entry 值；返回 (值类型名, 显示字符串) */
    private fun decodeEntryValue(r: ResourceEntry, e: Entry): Pair<String, String> {
        val resValue = runCatching { e.resValue }.getOrNull()
        if (resValue == null) return "unknown" to ""
        val valueType = runCatching { resValue.valueType }.getOrNull() ?: ValueType.NULL
        if (valueType.isReference()) return decodeReference(r, resValue.data)
        if (valueType.isColor()) {
            val color = runCatching<com.reandroid.graphics.AndroidColor> { e.valueAsColor }.getOrNull()
            return if (color != null) "color" to color.toHexString()
            else "color" to String.format("#%08X", resValue.data)
        }
        if (valueType == ValueType.DIMENSION) {
            return "dimension" to (runCatching { ComplexUtil.decodeComplex(false, resValue.data) }.getOrNull()
                ?: "0dp")
        }
        if (valueType == ValueType.FRACTION) {
            return "fraction" to (runCatching { ComplexUtil.decodeComplex(true, resValue.data) }.getOrNull()
                ?: "0%")
        }
        if (valueType == ValueType.STRING) {
            val s = runCatching { e.valueAsString }.getOrNull() ?: ""
            return "string" to s
        }
        if (valueType == ValueType.BOOLEAN) {
            val b = runCatching { e.valueAsBoolean }.getOrNull()
            return "bool" to (b?.toString() ?: "")
        }
        if (valueType == ValueType.FLOAT) {
            val f = runCatching { e.valueAsFloat }.getOrNull()
            return "float" to (f?.toString() ?: "")
        }
        if (valueType.isInteger()) {
            return "integer" to resValue.data.toString()
        }
        // array 类型：直接显示类型名
        val typeName = runCatching { r.type }.getOrNull() ?: ""
        if (typeName.contains("array", ignoreCase = true)) {
            return "array" to "数组资源"
        }
        val s = runCatching { e.valueAsString }.getOrNull()
        return if (s != null) (valueType.typeName.ifBlank { "value" }) to s
        else valueType.typeName.ifBlank { "value" } to String.format("0x%08X", resValue.data)
    }

    /** 递归解析资源引用；深度上限 6 防循环引用 */
    private fun decodeReference(r: ResourceEntry, data: Int): Pair<String, String> {
        var depth = 0
        var current: ResourceEntry = r
        var id = data
        while (depth < 6) {
            val target = runCatching { current.packageBlock.getResource(id) }.getOrNull()
                ?: runCatching {
                    current.packageBlock.tableBlock.getResource(id)
                }.getOrNull()
            if (target == null) {
                // 解析不到目标资源 → 输出引用形式
                val ref = runCatching {
                    current.buildReference(current.packageBlock, ValueType.REFERENCE)
                }.getOrNull()
                val refText = ref ?: String.format("@0x%08X", id)
                return "reference" to refText
            }
            val tv = runCatching { target.any()?.resValue }.getOrNull()
            if (tv == null) return "reference" to String.format("@0x%08X", id)
            val tt = runCatching { tv.valueType }.getOrNull() ?: ValueType.NULL
            if (!tt.isReference()) {
                // 目标不是引用 → 递归解码目标值（标注来源）
                val decoded = decodeEntryValue(target, target.any())
                return decoded.first to "@${target.type}/${target.name} → ${decoded.second}"
            }
            current = target
            id = tv.data
            depth++
        }
        return "reference" to String.format("@0x%08X", id)
    }

    override suspend fun saveResourceValue(id: Int, qualifiers: String?, newValue: String): Result<Unit> =
        runCatching {
            locked { m ->
                m.tableBlock.resources.forEach { r ->
                    if (r.resourceId != id) return@forEach
                    // qualifiers 为空 = 只改 default 变体；非空 = 精确改指定限定符变体
                    r.iterator().forEach { e ->
                        val cfg = runCatching { e.resConfig?.qualifiers }.getOrNull() ?: ""
                        val cfgNorm = cfg ?: ""
                        val qNorm = qualifiers ?: ""
                        val isDefault = cfgNorm.isEmpty() || cfgNorm == "default"
                        val match = if (qNorm.isEmpty()) isDefault else cfgNorm == qNorm
                        if (match) {
                            setEntryValueAuto(e, newValue)
                        }
                    }
                }
                modifiedArsc = true
            }
        }

    /**
     * 按文本自动编码写入条目值：
     *  - "@type/name" / "@0x..." → 引用
     *  - "#RRGGBB" / "#AARRGGBB" → color
     *  - "-330dp"/"16sp"/"0.5" → dimension / float
     *  - "true"/"false" → bool
     *  - "123" → integer
     *  - 其他 → string
     */
    private fun setEntryValueAuto(e: Entry, text: String) {
        val resValue = runCatching { e.resValue }.getOrNull() ?: return
        // 引用优先
        val trimmed = text.trim()
        if (trimmed.startsWith("@")) {
            val ref = runCatching {
                com.reandroid.arsc.coder.ValueCoder.encodeReference(e.packageBlock, trimmed)
            }.getOrNull()
            if (ref != null && !ref.isError) {
                resValue.setValue(ref)
                return
            }
        }
        // 通用自动编码
        val encoded = runCatching {
            com.reandroid.arsc.coder.ValueCoder.encode(trimmed)
        }.getOrNull()
        if (encoded != null && !encoded.isError) {
            resValue.setValue(encoded)
        } else {
            // 兜底按字符串写
            runCatching { e.setValueAsString(trimmed) }
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
        val apkFile = rawApkFile ?: throw IllegalStateException("尚未打开 APK")
        // NP 管理器方案：用 AssetManager.addAssetPath 加载 APK，然后 XmlResourceParser 解码 AXML
        val am = AssetManager::class.java.newInstance()
        val addAssetPath = AssetManager::class.java.getMethod("addAssetPath", String::class.java)
        addAssetPath.invoke(am, apkFile.absolutePath)
        val res = Resources(am, null, null)
        val parser = res.getAssets().openXmlResourceParser(path)
        val sb = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_DOCUMENT -> sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                XmlPullParser.START_TAG -> {
                    sb.append("<").append(parser.name)
                    for (i in 0 until parser.attributeCount) {
                        val name = parser.getAttributeName(i)
                        val value = parser.getAttributeValue(i)
                        sb.append(" ").append(name).append("=\"").append(value).append("\"")
                    }
                    sb.append(">\n")
                }
                XmlPullParser.END_TAG -> sb.append("</").append(parser.name).append(">\n")
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    if (text.isNotEmpty()) sb.append(text).append("\n")
                }
            }
            event = parser.next()
        }
        parser.close()
        sb.toString()
    }

    override suspend fun saveXmlFile(path: String, content: String): Result<Unit> = runCatching {
        locked { m ->
            val tmp = File(workDir(), "tmp/${path.substringAfterLast('/')}.xml")
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
            var outputPath: String = ""
            var outputName: String = ""
            var signedFlag: Boolean = false
            var outSize: Long = 0L
            mutex.withLock {
                val m = module ?: throw IllegalStateException("尚未打开 APK")
                val src = rawApkFile ?: throw IllegalStateException("尚未打开 APK")
                val unsigned = File(cacheDir(), "out/unsigned.apk")
                unsigned.parentFile?.mkdirs()
                writeUnsignedApk(src, unsigned, m)

                val base = apkFileName.removeSuffix(".apk").ifBlank { "output" }
                // 是否签名由设置决定，默认不签名：直接输出未签名 APK，交给用户自行签名。
                val toOutput: File
                if (OutputConfig.isSignEnabled()) {
                    val (key, cert) = loadOrCreateSigningKey()
                    val signedFile = File(cacheDir(), "out/signed.apk")
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
                    toOutput = signedFile
                    outputName = "${base}-signed.apk"
                    signedFlag = true
                } else {
                    toOutput = unsigned
                    outputName = "${base}-unsigned.apk"
                    signedFlag = false
                }
                outSize = toOutput.length()
                // 统一输出到配置目录
                outputPath = writeToOutput(toOutput, outputName)
            }
            // 记录到"保存的 APK"
            SavedApkStore.add(outputPath, outputName, outSize)
            BuildResult(outputName = outputName, signed = signedFlag, outputPath = outputPath)
        }
    }

    /** 重打包：遍历原始 APK，仅替换被修改的 dex/xml/arsc，其余条目原样拷贝 */
    private fun writeUnsignedApk(src: File, out: File, m: ApkModule) {
        // 若 arsc 被修改，预生成其二进制字节
        val arscBytes: ByteArray? = if (modifiedArsc) {
            val tmp = File(cacheDir(), "out/arsc.bin")
            // 关键：ARSCLib 自己的写入流程（ApkModuleEncoder.scanDirectory → refreshTable）
            // 在序列化 resource table 之前一定会先 refreshFull()，用于重建字符串池、
            // 各 chunk 偏移与 entry 索引。我们直接 writeBytes 会跳过这一步，
            // 写出内部偏移错乱的表 —— 表现为 MT 能打开但"目录结构不完整"，
            // 本 app 再打开时报
            // java.io.IOException: Error at:(idx,offset)Finished reading:...
            // （OffsetItem 读目标越界 → BlockReader 抛 EOFException）
            m.refreshTable()
            m.tableBlock.writeBytes(tmp)
            tmp.readBytes()
        } else null

        FileOutputStream(out).use { fos ->
            // 自己数流过的字节：ZipOutputStream 不暴露当前输出偏移，
            // 而对齐必须知道「本地头部将写在哪里」才能算出填充长度。
            val counter = CountingOutputStream(fos)
            ZipOutputStream(counter).use { zos ->
                ZipFile(src).use { zf ->
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        val name = e.name
                        // 只有被修改的条目才需要备好整块字节；未修改的沿用原条目元数据边读边写，
                        // 避免把几百 MB 的 assets 之类整个读进内存。
                        val replacement: ByteArray? = when {
                            modifiedDex.containsKey(name) -> modifiedDex[name]!!.readBytes()
                            modifiedXml.containsKey(name) -> modifiedXml[name]!!
                            name == "resources.arsc" && modifiedArsc -> arscBytes!!
                            else -> null
                        }
                        val isStored = e.method == ZipEntry.STORED
                        val nz = ZipEntry(name)
                        // 刻意不设 time：一旦设置，JDK 会自动往 extra 追加 9 字节的
                        // UT(0x5455) 扩展时间戳字段，本地头部长度超出预期，下面的对齐会算错。
                        nz.method = if (isStored) ZipEntry.STORED else ZipEntry.DEFLATED
                        var bytes: ByteArray? = replacement
                        if (bytes != null) {
                            nz.size = bytes.size.toLong()
                            nz.crc = crc32Of(bytes)
                        } else if (e.size >= 0 && e.crc >= 0) {
                            // 未修改条目：直接沿用原 zip 的元数据，边读边写，不占内存
                            nz.size = e.size
                            nz.crc = e.crc
                        } else {
                            // 极端情况：源条目缺 size/crc（带 data descriptor 的奇怪 zip）。
                            // STORED 没有这两个值 ZipOutputStream 会直接抛，只能读出来算。
                            bytes = zf.getInputStream(e).use { it.readBytes() }
                            nz.size = bytes.size.toLong()
                            nz.crc = crc32Of(bytes)
                        }
                        // STORED 还需要 compressed size（等于 uncompressed size）
                        if (isStored) nz.compressedSize = nz.size
                        // 进程内 zipalign：不压缩条目按 4 字节对齐，
                        // lib 下的 .so 按 4096 页对齐（高版本系统 dlopen 的硬要求）。
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

    /** 不压缩条目所需的对齐边界：lib 下的 .so 要求页对齐，其余 4 字节 */
    private fun alignmentOf(name: String): Int =
        if (name.startsWith("lib/") && name.endsWith(".so")) 4096 else 4

    /**
     * 算出使「数据起始偏移」满足 [align] 的 extra 字段。
     *
     * 数据偏移 = 本地头部起点 + 30（固定头长）+ 名字长度 + extra 长度，
     * 反解出 extra 长度即可。extra 内部沿用 Android 对齐惯例的 0xD935 ID，
     * 载荷补 0；不需要填充时返回空数组。
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
        if (ksFile.exists()) {
            // 密钥库可能因为上次写入被中断而损坏（PKCS12 只写了一半）。
            // 这里必须容错：否则打包会永久失败，而唯一的恢复手段是"清除应用数据"。
            val existing = runCatching {
                val ks = KeyStore.getInstance("PKCS12")
                ksFile.inputStream().use { ks.load(it, KS_PASS) }
                val key = ks.getKey(KS_ALIAS, KS_PASS) as PrivateKey
                val cert = ks.getCertificate(KS_ALIAS) as X509Certificate
                key to cert
            }.getOrNull()
            if (existing != null) return existing
            // 损坏就删掉重建（会导致产物签名变化，属可接受代价）
            runCatching { ksFile.delete() }
        }
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
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
        /** 当前活跃的服务实例（清除缓存时用它清内存树） */
        @Volatile
        var activeInstance: RealApkDataService? = null

        val KS_PASS = charArrayOf('a', 'p', 'k', 'e', 'd', 'i', 't', 'o', 'r')
        const val KS_ALIAS = "apkeditor"
    }
}

/**
 * 统计流经的字节数。
 *
 * ZipOutputStream 不暴露当前输出偏移，而对齐填充必须先知道本地头部会写在哪里，
 * 所以包一层自己数：所有字节都要从 [ZipOutputStream] 经这里落到文件。
 */
private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
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
