package com.apkeditor.miuix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.ui.component.BackNavigationIcon
import com.apkeditor.miuix.ui.util.AdaptiveTopAppBar
import com.apkeditor.miuix.ui.util.BlurredBar
import com.apkeditor.miuix.ui.util.LocalIsWideScreen
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 列表页统一顶栏（miuix 风格）。
 * 左：返回图标（统一格式，不再重复“返回”文字） + [navigationExtras]（返回右侧的扩展操作位）。
 * 右：Search 图标（切换搜索/过滤） + More 图标（下拉菜单）。
 *
 * **progressive 模式（主页同款）**：同时传入 [scrollBehavior]、[backdrop]、[scrollProgress]
 * 三件套时，顶栏走 AdaptiveTopAppBar —— 随滚动自动缩小（大标题 → 小标题），
 * 折叠后整条顶栏切换为 BlurredBar 的渐进模糊（内容透过顶栏可见），
 * 条件与主页/保存的APK/设置完全一致：`scrollProgress == 1f` 即视为折叠。
 * 不传则保持静态 TopAppBar（编辑器等场景沿用旧观感）。
 *
 * 所有列表类页面（XML 列表 / ARSC 列表 / Smali 树 / 类详情 / 所有类 等）统一使用。
 */
@Composable
fun MiuixTopBar(
    title: String,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    menuItems: List<DropdownItem> = emptyList(),
    /** 返回图标右侧的扩展操作位（如类详情页的“所有类”指南针） */
    navigationExtras: (@Composable RowScope.() -> Unit)? = null,
    /** progressive 三件套：滚动行为 + 模糊背景 + 滚动进度（0f 未折叠 / 1f 已折叠） */
    scrollBehavior: ScrollBehavior? = null,
    backdrop: LayerBackdrop? = null,
    scrollProgress: () -> Float = { 0f },
) {
    val navContent: @Composable RowScope.() -> Unit = {
        if (onBack != null || navigationExtras != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    BackNavigationIcon(onClick = onBack)
                }
                if (navigationExtras != null) {
                    Row(
                        modifier = Modifier.padding(start = 2.dp),
                        content = navigationExtras,
                    )
                }
            }
        }
    }
    val actionsContent: @Composable RowScope.() -> Unit = {
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = MiuixIcons.Search,
                    contentDescription = "搜索",
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        }
        if (menuItems.isNotEmpty()) {
            val entry = DropdownEntry(items = menuItems)
            OverlayIconDropdownMenu(entry = entry) {
                Icon(
                    imageVector = MiuixIcons.More,
                    contentDescription = "更多",
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
        }
    }

    if (scrollBehavior == null) {
        // 静态顶栏（编辑器页沿用）
        TopAppBar(
            title = title,
            subtitle = subtitle,
            navigationIcon = navContent,
            actions = actionsContent,
        )
    } else {
        // progressive：滚动缩小 + 折叠后渐进模糊（主页同款条件）
        val collapsed = scrollProgress() == 1f
        val blurActive = backdrop != null && collapsed
        val barColor = when {
            blurActive -> Color.Transparent
            collapsed -> MiuixTheme.colorScheme.surface
            else -> Color.Transparent
        }
        BlurredBar(backdrop, blurActive, scrollBehavior) {
            AdaptiveTopAppBar(
                title = title,
                subtitle = subtitle,
                showTopAppBar = true,
                isWideScreen = LocalIsWideScreen.current,
                scrollBehavior = scrollBehavior,
                color = barColor,
                navigationIcon = navContent,
                actions = actionsContent,
            )
        }
    }
}
