package com.apkeditor.miuix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.ui.component.BackNavigationIcon
import com.apkeditor.miuix.ui.components.TextField
import com.apkeditor.miuix.ui.util.BlurredBar
import com.apkeditor.miuix.ui.util.LocalIsWideScreen
import com.apkeditor.miuix.ui.util.pageContentPadding
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import com.apkeditor.miuix.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.RadioButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import androidx.compose.ui.state.ToggleableState
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 界面测试：Miuix 组件预览（参考 miuix example）。
 * 仅展示可交互组件，不接入业务逻辑。
 */
@Composable
fun TestHomePage(
    onBack: () -> Unit,
) {
    val isWideScreen = LocalIsWideScreen.current
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()
    val backdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, backdrop != null) {
                SmallTopAppBar(
                    title = "界面测试",
                    navigationIcon = { BackNavigationIcon(onClick = onBack) },
                    scrollBehavior = topAppBarScrollBehavior,
                    color = MiuixTheme.colorScheme.surface,
                    defaultWindowInsetsPadding = false,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
            val scrollPadding = pageContentPadding(
                innerPadding,
                PaddingValues(0.dp),
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
                // ---------- Button ----------
                item { SmallTitle("Button") }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                            ) { Text("主要按钮") }
                            Button(
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                enabled = false,
                            ) { Text("禁用") }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                text = "文本按钮",
                                onClick = {},
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = "文本按钮（禁用）",
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                enabled = false,
                            )
                        }
                    }
                }

                // ---------- FloatingActionButton ----------
                item { SmallTitle("FloatingActionButton") }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FloatingActionButton(onClick = {}) {
                                Text("+", style = MiuixTheme.textStyles.main)
                            }
                        }
                    }
                }

                // ---------- Switch ----------
                item { SmallTitle("Switch") }
                item {
                    var checked by remember { mutableStateOf(true) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        SwitchPreference(
                            title = "示例开关",
                            summary = "展示 Miuix SwitchPreference",
                            checked = checked,
                            onCheckedChange = { checked = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ---------- Switch (basic) ----------
                item { SmallTitle("Switch (basic)") }
                item {
                    var checked by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "基础开关",
                                style = MiuixTheme.textStyles.main,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = checked,
                                onCheckedChange = { checked = it },
                            )
                        }
                    }
                }

                // ---------- Checkbox ----------
                item { SmallTitle("Checkbox") }
                item {
                    var checked by remember { mutableStateOf(true) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        CheckboxPreference(
                            title = "示例复选框",
                            summary = "展示 Miuix CheckboxPreference",
                            checked = checked,
                            onCheckedChange = { checked = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ---------- Checkbox (basic) ----------
                item { SmallTitle("Checkbox (basic)") }
                item {
                    var state by remember { mutableStateOf(true) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "基础复选框",
                                style = MiuixTheme.textStyles.main,
                                modifier = Modifier.weight(1f),
                            )
                            Checkbox(
                                state = if (state) ToggleableState.On else ToggleableState.Off,
                                onClick = { state = !state },
                            )
                        }
                    }
                }

                // ---------- RadioButton ----------
                item { SmallTitle("RadioButton") }
                item {
                    var selected by remember { mutableIntStateOf(0) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            RadioButtonPreference(
                                title = "选项一",
                                summary = "展示 Miuix RadioButtonPreference",
                                selected = selected == 0,
                                onClick = { selected = 0 },
                            )
                            RadioButtonPreference(
                                title = "选项二",
                                selected = selected == 1,
                                onClick = { selected = 1 },
                            )
                        }
                    }
                }

                // ---------- RadioButton (basic) ----------
                item { SmallTitle("RadioButton (basic)") }
                item {
                    var selected by remember { mutableIntStateOf(0) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selected == 0,
                                    onClick = { selected = 0 },
                                )
                                Spacer(Modifier.weight(1f))
                                Text("选项一", style = MiuixTheme.textStyles.main)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selected == 1,
                                    onClick = { selected = 1 },
                                )
                                Spacer(Modifier.weight(1f))
                                Text("选项二", style = MiuixTheme.textStyles.main)
                            }
                        }
                    }
                }

                // ---------- Slider ----------
                item { SmallTitle("Slider") }
                item {
                    var sliderValue by remember { mutableFloatStateOf(0.5f) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "示例滑块",
                                    style = MiuixTheme.textStyles.main,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "%.2f".format(sliderValue),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.subtitle,
                                )
                            }
                            Slider(
                                value = sliderValue,
                                onValueChange = { sliderValue = it },
                                valueRange = 0f..1f,
                            )
                        }
                    }
                }

                // ---------- SliderPreference ----------
                item { SmallTitle("SliderPreference") }
                item {
                    var sliderValue by remember { mutableFloatStateOf(50f) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        SliderPreference(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            title = "示例滑块偏好",
                            summary = "展示 Miuix SliderPreference",
                            valueRange = 0f..100f,
                            steps = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // ---------- ProgressIndicator ----------
                item { SmallTitle("ProgressIndicator") }
                item {
                    var progress by remember { mutableFloatStateOf(0.6f) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                CircularProgressIndicator(
                                    progress = progress,
                                )
                                CircularProgressIndicator(
                                    progress = null,
                                )
                            }
                            LinearProgressIndicator(
                                progress = progress,
                            )
                            LinearProgressIndicator(
                                progress = null,
                            )
                            Slider(
                                value = progress,
                                onValueChange = { progress = it },
                                valueRange = 0f..1f,
                            )
                        }
                    }
                }

                // ---------- DropdownPreference ----------
                item { SmallTitle("DropdownPreference") }
                item {
                    var selectedIndex by remember { mutableIntStateOf(0) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        OverlayDropdownPreference(
                            items = listOf("选项一", "选项二", "选项三", "选项四"),
                            selectedIndex = selectedIndex,
                            title = "下拉偏好",
                            summary = "展示 Miuix OverlayDropdownPreference",
                            modifier = Modifier.fillMaxWidth(),
                            onSelectedIndexChange = { selectedIndex = it },
                        )
                    }
                }

                // ---------- SpinnerPreference ----------
                item { SmallTitle("SpinnerPreference") }
                item {
                    var selectedIndex by remember { mutableIntStateOf(0) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        OverlaySpinnerPreference(
                            items = listOf(
                                DropdownItem(text = "选项一"),
                                DropdownItem(text = "选项二"),
                                DropdownItem(text = "选项三"),
                            ),
                            selectedIndex = selectedIndex,
                            title = "弹窗偏好",
                            summary = "展示 Miuix OverlaySpinnerPreference",
                            modifier = Modifier.fillMaxWidth(),
                            onSelectedIndexChange = { selectedIndex = it },
                        )
                    }
                }

                // ---------- ArrowPreference ----------
                item { SmallTitle("ArrowPreference") }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        ArrowPreference(
                            title = "箭头偏好",
                            summary = "展示 Miuix ArrowPreference",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {},
                        )
                    }
                }

                // ---------- Card ----------
                item { SmallTitle("Card") }
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("卡片标题", style = MiuixTheme.textStyles.main)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "卡片用于分组展示相关内容，圆角与主题背景自动跟随。",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.subtitle,
                            )
                        }
                    }
                }

                // ---------- TextField ----------
                item { SmallTitle("TextField") }
                item {
                    var text by remember { mutableStateOf("") }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            TextField(
                                value = text,
                                onValueChange = { text = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = "输入示例",
                                useLabelAsPlaceholder = true,
                            )
                        }
                    }
                }

                // ---------- Dialog ----------
                item { SmallTitle("Dialog") }
                item {
                    var showDialog by remember { mutableStateOf(false) }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Button(onClick = { showDialog = true }) { Text("打开居中对话框") }
                        }
                    }

                    OverlayDialog(
                        show = showDialog,
                        title = "居中对话框",
                        summary = "largeScreen = true 强制居中展示（任意窗口宽度）。",
                        largeScreen = true,
                        onDismissRequest = { showDialog = false },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { showDialog = false },
                                modifier = Modifier.weight(1f),
                            ) { Text("取消") }
                            Button(
                                onClick = { showDialog = false },
                                modifier = Modifier.weight(1f),
                            ) { Text("确定") }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
