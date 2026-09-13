package com.apkeditor.miuix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 关于页（复刻 miuix 官方 AboutPage 结构 + foreground blur） */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showLicense by remember { mutableStateOf(false) }

    if (showLicense) {
        LicenseScreen(onBack = { showLicense = false })
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SmallTopAppBar(
                title = "关于",
                navigationIcon = {
                    Text("返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
                // 顶部 logo + 应用名 + 版本
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 应用图标（白色圆角方块）
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White),
                        ) {
                            Text(
                                text = "AE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 36.sp,
                                color = Color.Black,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "ApkEditor·Miuix",
                            color = MiuixTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "v0.1 (1)",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                        )
                    }
                }

                // 第一组卡片：项目链接
                item {
                    Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                        ArrowPreference(
                            title = "查看源码",
                            endActions = {
                                Text(
                                    text = "GitHub",
                                    fontSize = MiuixTheme.textStyles.subtitle.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = {
                                runCatching {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/XKspeed/ApkEditorMiuix"),
                                    )
                                    context.startActivity(intent)
                                }
                            },
                        )
                    }
                }

                // 第二组卡片：许可
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(top = 12.dp),
                    ) {
                        ArrowPreference(
                            title = "开源许可",
                            endActions = {
                                Text(
                                    text = "Apache-2.0",
                                    fontSize = MiuixTheme.textStyles.subtitle.fontSize,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            },
                            onClick = {
                                runCatching {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://www.apache.org/licenses/LICENSE-2.0.txt"),
                                    )
                                    context.startActivity(intent)
                                }
                            },
                        )
                        ArrowPreference(
                            title = "第三方开源许可",
                            onClick = { showLicense = true },
                        )
                    }
                }

                // 底部 spacer
                item {
                    Spacer(Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
                }
            }
    }
}

/** 第三方开源许可页 */
@Composable
private fun LicenseScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SmallTopAppBar(
                title = "第三方开源许可",
                navigationIcon = {
                    Text("返回",
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            .clickable(onClick = onBack),
                    )
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding,
        ) {
            item {
                Text(
                    "鸣谢",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
            }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("NP 管理器", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "APK 编辑功能设计参考",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            item {
                Text(
                    "开源组件",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
            }
            item {
                LicenseCard(
                    name = "Miuix",
                    author = "compose-miuix-ui",
                    license = "Apache License 2.0",
                    url = "https://github.com/compose-miuix-ui/miuix",
                )
            }
            item {
                LicenseCard(
                    name = "ARSCLib",
                    author = "REAndroid",
                    license = "Apache License 2.0",
                    url = "https://github.com/REAndroid/ARSCLib",
                )
            }
            item {
                LicenseCard(
                    name = "smali/baksmali",
                    author = "JesusFreke",
                    license = "BSD 3-Clause",
                    url = "https://github.com/JesusFreke/smali",
                )
            }
            item {
                LicenseCard(
                    name = "AndroidX Compose",
                    author = "Google",
                    license = "Apache License 2.0",
                    url = "https://developer.android.com/jetpack/androidx",
                )
            }
            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun LicenseCard(name: String, author: String, license: String, url: String) {
    val context = LocalContext.current
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(
            Modifier
                .clickable {
                    runCatching {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(url),
                        )
                        context.startActivity(intent)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "$author · $license",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                url,
                color = MiuixTheme.colorScheme.primary,
                fontSize = 12.sp,
            )
        }
    }
}
