// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package top.yukonga.miuix.kmp.nav.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.yukonga.miuix.kmp.nav.core.NavPresentationState

/**
 * Maps an entry's presentation snapshot (its `isRemoving` flag) and continuous relative depth to
 * the maximum [Lifecycle.State] that entry's [androidx.lifecycle.LifecycleOwner] may reach.
 */
internal fun navMaxLifecycleFor(presentation: NavPresentationState, d: Float, gestureActive: Boolean): Lifecycle.State = when {
    d <= -1f -> Lifecycle.State.DESTROYED
    presentation.isRemoving -> Lifecycle.State.CREATED
    d < -0.5f -> Lifecycle.State.STARTED
    d < 0.5f -> if (gestureActive) Lifecycle.State.STARTED else Lifecycle.State.RESUMED
    else -> Lifecycle.State.STARTED
}

/**
 * Remembers a per-entry [LifecycleOwner] whose maximum state is capped at [maxLifecycle].
 */
@Composable
internal fun rememberNavEntryLifecycleOwner(maxLifecycle: Lifecycle.State): LifecycleOwner {
    val parentLifecycleOwner = LocalLifecycleOwner.current
    return remember {
        object : LifecycleOwner {
            override val lifecycle: LifecycleRegistry = LifecycleRegistry(this)
        }.also { owner ->
            // 同步父生命周期
            owner.lifecycle.currentState = parentLifecycleOwner.lifecycle.currentState
        }
    }.also { owner ->
        // 限制最大状态
        if (owner.lifecycle.currentState > maxLifecycle) {
            owner.lifecycle.currentState = maxLifecycle
        }
    }
}

/**
 * Provides [owner] as the [LocalLifecycleOwner] for [content].
 */
@Composable
internal fun ProvideNavEntryLifecycle(
    owner: LifecycleOwner,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
}
