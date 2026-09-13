package com.apkeditor.miuix.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.apkeditor.miuix.ui.components.SectionCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.layout.WindowInsets

@Composable
fun HomeScreen(onPickApk: (String) -> Unit) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                onPickApk(uri.toString())
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = "ApkEditor·Miuix")
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            // 欢迎卡片
            SectionCard {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        "APK 反编译编辑工具",
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "支持 DEX / Smali 代码编辑、ARSC 资源编辑、二进制 XML 编辑，" +
                            "修改后可重新打包并签名。",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 功能说明
            SectionCard {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    FeatureDot("DEX 编辑", "反汇编为 Smali 代码，逐文件编辑后汇编回 DEX")
                    FeatureDot("ARSC 编辑", "浏览资源表，修改字符串值与资源名称")
                    FeatureDot("XML 编辑", "二进制 XML 解码为文本，修改后编码回写")
                    FeatureDot("打包签名", "重新打包 APK 并自动签名")
                }
            }

            Spacer(Modifier.height(24.dp))

            // 选择 APK
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/vnd.android.package-archive"
                    }
                    launcher.launch(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("选择 APK 文件")
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "提示：仅支持 Android 15+ 设备 · 目标为未加固 APK",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote2,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun FeatureDot(title: String, desc: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(
                desc,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.subtitle,
            )
        }
    }
}
