package com.apkeditor.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.data.ApkDataService
import com.apkeditor.miuix.data.SmaliClassDetail
import com.apkeditor.miuix.ui.component.CompassIcon
import com.apkeditor.miuix.ui.components.DialogActions
import com.apkeditor.miuix.ui.components.ErrorBox
import com.apkeditor.miuix.ui.components.InfoRow
import com.apkeditor.miuix.ui.components.ListItemRow
import com.apkeditor.miuix.ui.components.LoadingBox
import com.apkeditor.miuix.ui.components.MiuixDialog
import com.apkeditor.miuix.ui.components.MiuixTopBar
import com.apkeditor.miuix.ui.components.SectionCard
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import com.apkeditor.miuix.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 类详情页（MT 风格）：类头信息 + 方法列表。
 *
 * 交互：
 *  - 顶栏左侧：返回（统一 BackNavigationIcon） + **指南针**（点开进入“所有类”列表，快速切换类）
 *  - 顶栏菜单：编辑整个文件（整文件 smali 编辑）
 *  - 点方法 → 单方法编辑页（只加载该方法块，保存回写该块）
 */
@Composable
fun SmaliClassPage(
    dexName: String,
    filePath: String,
    dexNames: List<String>,
    service: ApkDataService,
    onBack: () -> Unit,
    onOpenClassList: () -> Unit,
    onOpenMethod: (methodIndex: Int, methodHeader: String) -> Unit,
    onEditFile: () -> Unit,
) {
    var detail by remember { mutableStateOf<SmaliClassDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showFileTip by remember { mutableStateOf(false) }

    LaunchedEffect(dexName, filePath) {
        error = null
        detail = null
        service.readSmaliClassDetail(dexName, filePath)
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "解析类失败" }
    }

    // 主页同款 progressive 顶栏：滚动缩小 + 折叠后模糊（此页用 verticalScroll，按偏移判断）
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val scrollState = rememberScrollState()
    val scrollProgress by remember {
        derivedStateOf { if (scrollState.value > 0) 1f else 0f }
    }
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MiuixTopBar(
                title = detail?.className?.substringAfterLast('.') ?: filePath.substringAfterLast('/'),
                subtitle = detail?.className ?: "",
                onBack = onBack,
                navigationExtras = {
                    // 指南针：点开显示所有类
                    IconButton(onClick = onOpenClassList) {
                        CompassIcon(tint = MiuixTheme.colorScheme.onBackground)
                    }
                },
                menuItems = listOf(
                    DropdownItem(text = "编辑整个文件", onClick = { showFileTip = true }),
                ),
                scrollBehavior = topAppBarScrollBehavior,
                backdrop = backdrop,
                scrollProgress = { scrollProgress },
            )
        },
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        Column(
            Modifier.fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .pageScrollModifiers(
                    showTopAppBar = true,
                    topAppBarScrollBehavior = topAppBarScrollBehavior,
                ),
        ) {
            when {
                error != null -> ErrorBox(error!!, onRetry = null)
                detail == null -> LoadingBox("解析类…")
                else -> {
                    val d = detail!!
                    // 类头信息
                    SectionCard {
                        InfoRow("类名", d.className.ifEmpty { "（未知）" })
                        if (d.access.isNotEmpty()) InfoRow("访问", d.access)
                        if (d.superName.isNotEmpty()) InfoRow("父类", d.superName)
                        if (d.interfaces.isNotEmpty()) {
                            InfoRow("接口", d.interfaces.joinToString("\n"))
                        }
                    }
                    Text(
                        text = "方法（" + d.methods.size + "）",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.subtitle,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                    if (d.methods.isEmpty()) {
                        Text(
                            text = "该类没有方法",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.subtitle,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    } else {
                        d.methods.forEach { m ->
                            HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                            ListItemRow(
                                title = m.name,
                                subtitle = buildString {
                                    if (m.access.isNotEmpty()) {
                                        append(m.access)
                                        append(' ')
                                    }
                                    append(m.proto)
                                },
                                trailing = "›",
                                onClick = { onOpenMethod(m.index, m.header) },
                            )
                        }
                    }
                    HorizontalDivider(color = MiuixTheme.colorScheme.dividerLine)
                    Spacer(Modifier.fillMaxWidth().height(24.dp))
                }
            }
        }
        }
    }

    if (showFileTip) {
        MiuixDialog(title = "编辑整个文件", onDismiss = { showFileTip = false }) {
            Text(
                text = "将打开该类的完整 smali 文件进行编辑。若只想改某个方法，直接点方法列表更安全。",
                color = MiuixTheme.colorScheme.onSurface,
            )
            DialogActions(
                confirmText = "打开",
                onConfirm = {
                    showFileTip = false
                    onEditFile()
                },
                cancelText = "取消",
                onCancel = { showFileTip = false },
            )
        }
    }
}
