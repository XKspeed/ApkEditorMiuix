package com.apkeditor.miuix.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.apkeditor.miuix.ui.component.BackNavigationIcon
import com.apkeditor.miuix.ui.util.BlurredBar
import com.apkeditor.miuix.ui.util.pageContentPadding
import com.apkeditor.miuix.ui.util.pageScrollModifiers
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

data class Library(
    val name: String,
    val version: String,
    val license: String,
    val website: String,
)

private val libraries = listOf(
    Library(
        name = "Compose Multiplatform",
        version = "1.7.3",
        license = "Apache License 2.0",
        website = "https://github.com/JetBrains/compose-multiplatform",
    ),
    Library(
        name = "Kotlin Coroutines",
        version = "1.11.0",
        license = "Apache License 2.0",
        website = "https://github.com/Kotlin/kotlinx.coroutines",
    ),
    Library(
        name = "KotlinX Serialization",
        version = "1.7.3",
        license = "Apache License 2.0",
        website = "https://github.com/Kotlin/kotlinx.serialization",
    ),
    Library(
        name = "AndroidX Activity Compose",
        version = "1.11.0",
        license = "Apache License 2.0",
        website = "https://developer.android.com/jetpack/androidx/releases/activity",
    ),
    Library(
        name = "AndroidX Lifecycle",
        version = "2.11.0",
        license = "Apache License 2.0",
        website = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
    ),
    Library(
        name = "Miuix",
        version = "0.9.3",
        license = "Apache License 2.0",
        website = "https://github.com/compose-miuix-ui/miuix",
    ),
    Library(
        name = "miuix-nav",
        version = "0.9.3",
        license = "Apache License 2.0",
        website = "https://github.com/compose-miuix-ui/miuix",
    ),
    Library(
        name = "Navigation Event",
        version = "1.1.2",
        license = "Apache License 2.0",
        website = "https://developer.android.com/jetpack/androidx/releases/navigationevent",
    ),
    Library(
        name = "ARSCLib",
        version = "V1.4.0",
        license = "Apache License 2.0",
        website = "https://github.com/REAndroid/ARSCLib",
    ),
    Library(
        name = "baksmali",
        version = "2.5.2",
        license = "BSD 3-Clause",
        website = "https://github.com/JesusFreke/smali",
    ),
    Library(
        name = "smali",
        version = "2.5.2",
        license = "BSD 3-Clause",
        website = "https://github.com/JesusFreke/smali",
    ),
    Library(
        name = "apksig",
        version = "8.13.2",
        license = "Apache License 2.0",
        website = "https://android.googlesource.com/platform/tools/apksig",
    ),
    Library(
        name = "Bouncy Castle",
        version = "1.78.1",
        license = "MIT License",
        website = "https://www.bouncycastle.org/",
    ),
    Library(
        name = "sora-editor",
        version = "0.23.6",
        license = "GNU Lesser General Public License v3.0",
        website = "https://github.com/Rosemoe/sora-editor",
    ),
    Library(
        name = "PhotoView",
        version = "2.3.0",
        license = "Apache License 2.0",
        website = "https://github.com/Baseflow/PhotoView",
    ),
    Library(
        name = "Subsampling Scale Image View",
        version = "3.10.0",
        license = "Apache License 2.0",
        website = "https://github.com/davemorrissey/subsampling-scale-image-view",
    ),
    Library(
        name = "Markwon",
        version = "4.6.2",
        license = "Apache License 2.0",
        website = "https://github.com/noties/Markwon",
    ),
    Library(
        name = "Haze",
        version = "1.7.3",
        license = "Apache License 2.0",
        website = "https://github.com/chrisbanes/haze",
    ),
)

@Composable
fun ThirdPartyLicensesPage(onBack: () -> Unit) {
    val topAppBarScrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    val backdrop = rememberBlurBackdrop()

    Scaffold(
        topBar = {
            BlurredBar(backdrop, true) {
                SmallTopAppBar(
                    title = "第三方许可证",
                    navigationIcon = {
                        BackNavigationIcon(onClick = onBack)
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    defaultWindowInsetsPadding = false,
                )
            }
        },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        val uriHandler = LocalUriHandler.current
        Box {
            val scrollPadding = pageContentPadding(
                innerPadding,
                innerPadding,
                false,
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
                    bottom = innerPadding.calculateBottomPadding(),
                ),
            ) {
                item {
                    SmallTitle("开源依赖")
                }
                libraries.forEach { library ->
                    item {
                        Card(
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        ) {
                            ArrowPreference(
                                title = library.name,
                                summary = "${library.version} · ${library.license}",
                                onClick = { uriHandler.openUri(library.website) },
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}
