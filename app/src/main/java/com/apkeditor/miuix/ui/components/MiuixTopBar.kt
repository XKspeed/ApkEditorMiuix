package com.apkeditor.miuix.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.apkeditor.miuix.ui.component.BackNavigationIcon

/**
 * 列表页统一顶栏（miuix 风格）。
 * 左：返回图标（统一格式，不再重复“返回”文字） + [navigationExtras]（返回右侧的扩展操作位）。
 * 右：Search 图标（切换搜索/过滤） + More 图标（下拉菜单）。
 *
 * 所有列表类页面（XML 列表 / ARSC 列表 / Smali 树 / 类详情 / APK 内容 / 关于 等）统一使用。
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
) {
    TopAppBar(
        title = title,
        subtitle = subtitle,
        navigationIcon = {
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
        },
        actions = {
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
        },
    )
}
