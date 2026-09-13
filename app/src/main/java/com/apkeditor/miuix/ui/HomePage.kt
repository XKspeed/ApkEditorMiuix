package com.apkeditor.miuix.ui

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.MiuixDialog
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * NP 管理器风格主页（纯 miuix 组件）：
 * 文件浏览器，浏览手机存储，点文件夹进入，点 .apk 弹出操作菜单（安装/反编译）。
 */
@Composable
fun HomePage(onPickApk: (String) -> Unit) {
    val context = LocalContext.current
    var currentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    var selectedApk by remember { mutableStateOf<File?>(null) }
    var needPermission by remember {
        mutableStateOf(!Environment.isExternalStorageManager())
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

    val files = remember(currentDir) {
        currentDir.listFiles()?.toList()?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(
            title = currentDir.absolutePath,
            scrollBehavior = top.yukonga.miuix.kmp.basic.MiuixScrollBehavior(),
        ) }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            // 上级目录
            Row(
                Modifier.fillMaxWidth().clickable {
                    currentDir.parentFile?.let { if (it.canRead()) currentDir = it }
                }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("..", fontWeight = FontWeight.Medium)
            }
            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)

            LazyColumn(Modifier.fillMaxSize()) {
                items(files) { file ->
                    val isApk = file.extension.equals("apk", true)
                    ListItemRow(
                        title = (if (file.isDirectory) "📁 " else "") + file.name,
                        subtitle = if (file.isDirectory) "${file.list()?.size ?: 0} 项"
                        else formatSize(file.length()),
                        trailing = "›",
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
        }
    }

    // APK 操作菜单
    selectedApk?.let { apk ->
        MiuixDialog(
            title = apk.name,
            onDismiss = { selectedApk = null },
        ) {
            Text("大小: ${formatSize(apk.length())}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            Spacer(Modifier.height(4.dp))
            Text("路径: ${apk.absolutePath}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
            DialogActions(
                confirmText = "反编译",
                onConfirm = {
                    onPickApk(Uri.fromFile(apk).toString())
                    selectedApk = null
                },
                cancelText = "安装",
                onCancel = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.fromFile(apk), "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    selectedApk = null
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
