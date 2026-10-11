package com.apkeditor.miuix.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkVersionService
import com.apkeditor.miuix.data.OutputConfig
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.MiuixDialog
import com.apkeditor.miuix.ui.components.TextField
import com.apkeditor.miuix.ui.util.BlurredBar
import com.apkeditor.miuix.ui.util.LocalIsWideScreen
import com.apkeditor.miuix.ui.util.pageContentPadding
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import top.yukonga.miuix.kmp.blur.layerBackdrop
import com.apkeditor.miuix.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun HomePage(
    padding: PaddingValues,
    onPickApk: (String) -> Unit,
    /** 是否允许“返回键=上一级目录”（仅导航栈顶时生效，二级页内不抢返回键） */
    enableBackToParent: Boolean = true,
) {
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var selectedApk by remember { mutableStateOf<File?>(null) }
    var needPermission by remember {
        mutableStateOf(!Environment.isExternalStorageManager())
    }
    /** 长按顶栏弹出的路径跳转对话框 */
    var showJump by remember { mutableStateOf(false) }
    var jumpPath by remember { mutableStateOf("") }
    var jumpError by remember { mutableStateOf<String?>(null) }

    // ---- 快速编辑（版本号）----
    val scope = rememberCoroutineScope()
    /** 正在快速编辑的 APK；null = 弹窗关闭 */
    var quickEditApk by remember { mutableStateOf<File?>(null) }
    var veName by remember { mutableStateOf("") }
    var veCode by remember { mutableStateOf("") }
    var veBusy by remember { mutableStateOf(false) }
    var veError by remember { mutableStateOf<String?>(null) }
    var veInfo by remember { mutableStateOf<String?>(null) }

    /** 返回上一级目录（点顶栏 / 系统返回键共用） */
    fun goUp() {
        currentDir.parentFile?.let { if (it.canRead()) currentDir = it }
    }

    if (needPermission) {
        MiuixDialog(
            title = "需要存储权限",
            onDismiss = { needPermission = false },
        ) {
            Text(
                "NP 管理器风格主页需要\"所有文件访问\"权限才能浏览手机存储。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            DialogActions(
                confirmText = "去设置",
                onConfirm = {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                    needPermission = false
                },
                cancelText = "跳过",
                onCancel = { needPermission = false },
            )
        }
        return
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    // 在具体目录里按系统返回键 → 返回上一级目录（存储根目录 / 已进二级页时不拦截，交给外层）
    val storageRoot = remember { Environment.getExternalStorageDirectory().absolutePath }
    BackHandler(
        enabled = enableBackToParent &&
            currentDir.absolutePath != storageRoot &&
            currentDir.parentFile != null,
    ) {
        goUp()
    }

    val scrollProgress by remember {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f
                else -> 0f
            }
        }
    }

    val backdrop = rememberBlurBackdrop()
    val collapsed by remember { derivedStateOf { scrollProgress == 1f } }
    val blurActive by remember(backdrop) { derivedStateOf { backdrop != null && scrollProgress == 1f } }

    val files = remember(currentDir) {
        currentDir.listFiles()?.toList()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()
    }

    Scaffold(
        topBar = {
            val barColor = if (blurActive) {
                androidx.compose.ui.graphics.Color.Transparent
            } else {
                if (collapsed) MiuixTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent
            }
            BlurredBar(backdrop, blurActive) {
                // 点顶栏 = 返回上一级；长按顶栏 = 弹窗输入目录路径跳转
                Box(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(currentDir) {
                            detectTapGestures(
                                onTap = { goUp() },
                                onLongPress = {
                                    jumpPath = currentDir.absolutePath
                                    jumpError = null
                                    showJump = true
                                },
                            )
                        }
                ) {
                    SmallTopAppBar(
                        title = currentDir.absolutePath,
                        scrollBehavior = topAppBarScrollBehavior,
                        color = barColor,
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            val scrollPadding = pageContentPadding(
                innerPadding,
                padding,
                isWideScreen,
                extraStart = WindowInsets.displayCutout.asPaddingValues().calculateLeftPadding(LayoutDirection.Ltr),
                extraEnd = WindowInsets.displayCutout.asPaddingValues().calculateRightPadding(LayoutDirection.Ltr),
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(
                        showTopAppBar = true,
                        topAppBarScrollBehavior = topAppBarScrollBehavior,
                    ),
                contentPadding = PaddingValues(
                    top = scrollPadding.calculateTopPadding(),
                    start = scrollPadding.calculateLeftPadding(LayoutDirection.Ltr),
                    end = scrollPadding.calculateRightPadding(LayoutDirection.Ltr),
                    bottom = scrollPadding.calculateBottomPadding(),
                ),
            ) {
                items(files) { file ->
                    val isApk = file.extension.equals("apk", true)
                    ListItemRow(
                        title = (if (file.isDirectory) "\uD83D\uDCC1 " else "") + file.name,
                        subtitle = if (file.isDirectory) "${file.list()?.size ?: 0} 项"
                        else formatSize(file.length()),
                        trailing = "\u203A",
                        onClick = {
                            when {
                                file.isDirectory -> currentDir = file
                                isApk -> selectedApk = file
                            }
                        },
                    )
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                }
            }

            selectedApk?.let { apk ->
                OverlayDialog(
                    show = true,
                    title = apk.name,
                    summary = apk.absolutePath,
                    largeScreen = true,
                    onDismissRequest = { selectedApk = null },
                ) {
                    Text(
                        "大小: ${formatSize(apk.length())}",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // 快速编辑：读/改版本号（versionName / versionCode），签名与否由设置开关决定
                        Button(
                            onClick = {
                                quickEditApk = apk
                                veName = ""
                                veCode = ""
                                veError = null
                                veInfo = "读取版本号…"
                                veBusy = false
                                selectedApk = null
                                scope.launch {
                                    runCatching { ApkVersionService.readVersion(apk) }
                                        .onSuccess { v ->
                                            veName = v.versionName ?: ""
                                            veCode = v.versionCode ?: ""
                                            veInfo = if (v.versionName == null && v.versionCode == null) {
                                                "该 APK 清单中没有版本号属性"
                                            } else {
                                                "当前: versionName=" + (v.versionName ?: "—") +
                                                    " / versionCode=" + (v.versionCode ?: "—")
                                            }
                                        }
                                        .onFailure {
                                            veInfo = null
                                            veError = "读取失败：" + it.message
                                        }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("快速编辑") }

                        Button(
                            onClick = {
                                onPickApk(Uri.fromFile(apk).toString())
                                selectedApk = null
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("反编译") }
                    }
                }
            }
            // ---- 快速编辑弹窗：版本号编辑 ----
            quickEditApk?.let { apk ->
                OverlayDialog(
                    show = true,
                    title = "快速编辑",
                    summary = apk.name,
                    largeScreen = true,
                    onDismissRequest = { if (!veBusy) quickEditApk = null },
                ) {
                    Text(
                        "APK 版本号",
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.main,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (veInfo != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            veInfo!!,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    TextField(
                        value = veName,
                        onValueChange = { veName = it },
                        label = "versionName（留空=不改）",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !veBusy,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = veCode,
                        onValueChange = { veCode = it.filter { c -> c.isDigit() }.take(10) },
                        label = "versionCode（留空=不改）",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !veBusy,
                    )
                    if (veError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            veError!!,
                            color = MiuixTheme.colorScheme.error,
                            style = MiuixTheme.textStyles.subtitle,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (OutputConfig.isSignEnabled()) "输出：签名（可在设置中关闭）"
                        else "输出：未签名（可在设置中开启签名）",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { if (!veBusy) quickEditApk = null },
                            modifier = Modifier.weight(1f),
                            enabled = !veBusy,
                        ) { Text("取消") }

                        Button(
                            onClick = {
                                val name = veName.trim().ifBlank { null }
                                val code = veCode.trim().toIntOrNull()
                                if (name == null && code == null) {
                                    veError = "请至少填写一项（versionName 或 versionCode）"
                                    return@Button
                                }
                                veError = null
                                veBusy = true
                                veInfo = "正在重打包…"
                                scope.launch {
                                    runCatching { ApkVersionService.quickEdit(context, apk, name, code) }
                                        .onSuccess { res ->
                                            veBusy = false
                                            val signTag = if (res.signed) "已签名" else "未签名"
                                            veInfo = "完成（" + signTag + "）：" + res.outputPath
                                            Toast.makeText(
                                                context,
                                                "快速编辑完成：" + res.outputName,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                        .onFailure { e ->
                                            veBusy = false
                                            veInfo = null
                                            veError = "失败：" + e.message
                                        }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !veBusy,
                        ) { Text(if (veBusy) "处理中…" else "保存") }
                    }
                }
            }
        }
    }

    if (showJump) {
        MiuixDialog(
            title = "跳转到目录",
            onDismiss = { showJump = false },
        ) {
            TextField(
                value = jumpPath,
                onValueChange = { jumpPath = it },
                label = "目录路径",
                modifier = Modifier.fillMaxWidth(),
            )
            if (jumpError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    jumpError!!,
                    color = MiuixTheme.colorScheme.error,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
            DialogActions(
                confirmText = "跳转",
                onConfirm = {
                    val raw = jumpPath.trim()
                    val f = File(raw)
                    when {
                        raw.isEmpty() -> jumpError = "请输入目录路径"
                        !f.exists() -> jumpError = "路径不存在：$raw"
                        !f.isDirectory -> jumpError = "不是目录：$raw"
                        !f.canRead() -> jumpError = "没有读取权限：$raw"
                        else -> {
                            currentDir = f
                            jumpError = null
                            showJump = false
                        }
                    }
                },
                cancelText = "取消",
                onCancel = {
                    jumpError = null
                    showJump = false
                },
            )
        }
    }

}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return when {
        bytes < 1024 -> "$bytes B"
        kb < 1024 -> String.format("%.1f KB", kb)
        else -> String.format("%.1f MB", kb / 1024.0)
    }
}
