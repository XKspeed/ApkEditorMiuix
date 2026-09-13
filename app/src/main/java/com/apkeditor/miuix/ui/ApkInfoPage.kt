package com.apkeditor.miuix.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.ApkEntry
import com.apkeditor.miuix.data.ApkInfo
import com.apkeditor.miuix.data.BuildResult
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.InfoRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.MiuixDialog
import com.apkeditor.miuix.ui.components.SectionCard
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * APK 内容页（NP 管理器风格）：
 * 顶部为 APK 概览（图标/名称/版本/包名/权限），下方列出 APK 内部文件，
 * 点击 AndroidManifest.xml / classes.dex / resources.arsc / res/ 进入对应编辑功能。
 */
@Composable
fun ApkInfoPage(
    uri: String,
    service: ApkDataService,
    onBack: () -> Unit,
    onOpenManifest: () -> Unit,
    onOpenDex: (String) -> Unit,
    onOpenAllDex: (List<String>) -> Unit,
    onOpenArsc: () -> Unit,
    onOpenRes: () -> Unit,
) {
    var info by remember { mutableStateOf<ApkInfo?>(null) }
    var contents by remember { mutableStateOf<List<ApkEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var building by remember { mutableStateOf(false) }
    var buildResult by remember { mutableStateOf<BuildResult?>(null) }
    var buildError by remember { mutableStateOf<String?>(null) }
    var unsupported by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uri) {
        service.loadApk(uri)
            .onSuccess { info = it }
            .onFailure { error = it.message ?: "无法解析 APK" }
        // 直接解压 APK 做内容预览（快）：与解析并行，不等解析完成
        service.listApkContents()
            .onSuccess { contents = it }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = info?.label ?: "APK 内容",
                navigationIcon = {
                    Text(
                        "返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
            )
        }
    ) { innerPadding ->
        when {
            error != null -> ErrorBox(error!!, onRetry = null)
            info == null -> LoadingBox("正在解析 APK…")
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                item {
                    ApkHeader(info!!)
                    Spacer(Modifier.height(8.dp))
                }
                item {
                    Text(
                        "APK 内容",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    )
                }
                item {
                    SectionCard {
                        val entries = buildFileEntries(info!!, contents)
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                            FileListRow(
                                icon = entry.icon,
                                name = entry.name,
                                size = entry.size,
                                folder = entry.isFolder,
                                onClick = {
                                    when (val action = entry.action) {
                                        is EntryAction.Manifest -> onOpenManifest()
                                        is EntryAction.Dex -> onOpenDex(action.name)
                                        is EntryAction.AllDex -> onOpenAllDex(action.names)
                                        is EntryAction.Arsc -> onOpenArsc()
                                        is EntryAction.Res -> onOpenRes()
                                        is EntryAction.NotSupported ->
                                            unsupported = entry.name
                                    }
                                },
                            )
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            building = true
                            buildError = null
                            scope.launch {
                                service.buildAndSign()
                                    .onSuccess { buildResult = it }
                                    .onFailure { buildError = it.message ?: "打包失败" }
                                building = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        enabled = !building,
                    ) {
                        Text(if (building) "正在打包签名…" else "打包并签名（输出到统一目录）")
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    // 打包结果弹窗
    if (buildResult != null) {
        MiuixDialog(
            title = "打包完成",
            onDismiss = { buildResult = null },
        ) {
            Text(
                "已生成签名 APK：${buildResult!!.outputName}",
                color = MiuixTheme.colorScheme.onSurface,
            )
            DialogActions(
                confirmText = "好的",
                onConfirm = { buildResult = null },
                cancelText = "",
                onCancel = { buildResult = null },
            )
        }
    }
    if (buildError != null) {
        MiuixDialog(
            title = "打包失败",
            onDismiss = { buildError = null },
        ) {
            Text(buildError!!, color = MiuixTheme.colorScheme.error)
            DialogActions(
                confirmText = "知道了",
                onConfirm = { buildError = null },
                cancelText = "",
                onCancel = { buildError = null },
            )
        }
    }
    if (unsupported != null) {
        MiuixDialog(
            title = unsupported!!,
            onDismiss = { unsupported = null },
        ) {
            Text(
                "该模块将在接入真实反编译引擎后支持编辑",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            DialogActions(
                confirmText = "知道了",
                onConfirm = { unsupported = null },
                cancelText = "",
                onCancel = { unsupported = null },
            )
        }
    }
}

/** 文件列表条目 */
private data class FileEntry(
    val icon: ImageVector,
    val name: String,
    val size: String,
    val isFolder: Boolean,
    val action: EntryAction,
)

private sealed interface EntryAction {
    data object Manifest : EntryAction
    data class Dex(val name: String) : EntryAction
    data class AllDex(val names: List<String>) : EntryAction
    data object Arsc : EntryAction
    data object Res : EntryAction
    data object NotSupported : EntryAction
}

/** 构造 MT 风格 APK 文件列表：基于 zip 直接解压的真实条目 */
private fun buildFileEntries(info: ApkInfo, contents: List<ApkEntry>?): List<FileEntry> {
    val list = mutableListOf<FileEntry>()
    fun entry(name: String): ApkEntry? = contents?.firstOrNull { it.path == name }
    fun sizeStr(e: ApkEntry?): String = e?.size?.let { formatSize(it) } ?: ""

    // AndroidManifest.xml
    val manifest = entry("AndroidManifest.xml")
    list.add(
        FileEntry(
            icon = MiuixIcons.File,
            name = "AndroidManifest.xml",
            size = sizeStr(manifest),
            isFolder = false,
            action = EntryAction.Manifest,
        )
    )
    // 多 dex 时：全部反编译入口
    if (info.dexNames.size > 1) {
        list.add(
            FileEntry(
                icon = MiuixIcons.File,
                name = "全部 DEX 同时反编译",
                size = "${info.dexNames.size} 个",
                isFolder = false,
                action = EntryAction.AllDex(info.dexNames),
            )
        )
    }
    // 每个 dex（真实大小）
    info.dexNames.forEach { dex ->
        list.add(
            FileEntry(
                icon = MiuixIcons.File,
                name = dex,
                size = sizeStr(entry(dex)),
                isFolder = false,
                action = EntryAction.Dex(dex),
            )
        )
    }
    // resources.arsc
    val arsc = entry("resources.arsc")
    list.add(
        FileEntry(
            icon = MiuixIcons.File,
            name = "resources.arsc",
            size = sizeStr(arsc),
            isFolder = false,
            action = EntryAction.Arsc,
        )
    )
    // 资源 XML 编辑（NP 风格：反编译成 values/*.xml 编辑）
    list.add(
        FileEntry(
            icon = MiuixIcons.File,
            name = "资源 XML 编辑",
            size = "",
            isFolder = false,
            action = EntryAction.Res,
        )
    )
    // 顶层目录（res/ assets/ lib/ META-INF/ 等），聚合条目数与总大小
    val dirs = LinkedHashMap<String, MutableList<ApkEntry>>()
    contents?.forEach { e ->
        val slash = e.path.indexOf('/')
        if (slash > 0) {
            val top = e.path.substring(0, slash + 1)
            dirs.getOrPut(top) { mutableListOf() }.add(e)
        }
    }
    dirs.forEach { (dir, subs) ->
        val total = subs.sumOf { it.size }
        val icon = if (dir == "res/") MiuixIcons.Folder else MiuixIcons.Folder
        list.add(
            FileEntry(
                icon = icon,
                name = dir,
                size = "${subs.size} 项 · ${formatSize(total)}",
                isFolder = true,
                action = if (dir == "res/") EntryAction.Res else EntryAction.NotSupported,
            )
        )
    }
    return list
}

private fun formatSize(size: Long): String {
    val kb = size / 1024.0
    return when {
        size < 1024 -> "$size B"
        kb < 1024 -> String.format("%.1f KB", kb)
        else -> String.format("%.1f MB", kb / 1024.0)
    }
}

/** MT 风格头部：应用图标 + 名称 + 版本/包名概览 */
@Composable
private fun ApkHeader(info: ApkInfo) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 应用图标（占位：名称首字符）
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        info.label.take(1).ifBlank { "A" },
                        color = MiuixTheme.colorScheme.onPrimary,
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(info.label, style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    "${info.versionName} (${info.versionCode}) · ${info.fileSize}",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.subtitle,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    info.packageName,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
        }
        HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
        InfoRow("SDK", "min ${info.minSdk} · target ${info.targetSdk}")
        InfoRow("DEX", "${info.dexNames.size} 个")
        InfoRow("资源", "${info.resourceCount} 项")
        InfoRow("入口", info.mainActivity)
        if (info.permissions.isNotEmpty()) {
            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
            InfoRow("权限", "${info.permissions.size} 项")
        }
    }
}

/** 文件列表行：图标 + 名称 + 大小 */
@Composable
private fun FileListRow(
    icon: ImageVector,
    name: String,
    size: String,
    folder: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 文件/目录图标（MiuixIcons）
        Image(
            imageVector = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(
                if (folder) MiuixTheme.colorScheme.primary
                else MiuixTheme.colorScheme.onSurfaceVariantActions
            ),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            name,
            style = MiuixTheme.textStyles.main,
            modifier = Modifier.weight(1f),
        )
        if (size.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(
                size,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
                textAlign = TextAlign.End,
            )
        }
    }
}
