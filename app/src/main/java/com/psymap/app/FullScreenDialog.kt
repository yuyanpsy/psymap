package com.psymap.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * 全局全屏页面管理器。
 * 页面内容渲染在 PsyMapApp 的 Scaffold 之上，覆盖整个屏幕。
 * 不创建新 Window，status bar 颜色永远不变。
 */
object FullScreenPages {
    // 页面栈：支持多层嵌套（如 错题本 -> 题目详情）
    val stack = mutableStateListOf<FullScreenEntry>()

    data class FullScreenEntry(
        val id: String,
        val dismissOnBackPress: Boolean,
        val onDismiss: () -> Unit,
        val content: @Composable () -> Unit
    )

    fun show(id: String, dismissOnBackPress: Boolean = true, onDismiss: () -> Unit, content: @Composable () -> Unit) {
        // 避免重复添加
        if (stack.none { it.id == id }) {
            stack.add(FullScreenEntry(id, dismissOnBackPress, onDismiss, content))
        }
    }

    fun dismiss(id: String) {
        stack.removeAll { it.id == id }
    }

    fun dismissTop() {
        if (stack.isNotEmpty()) {
            val top = stack.last()
            top.onDismiss()
            stack.removeAt(stack.size - 1)
        }
    }
}

/**
 * 在 PsyMapApp 中调用，渲染全屏页面栈。
 */
@Composable
fun RenderFullScreenPages() {
    for (entry in FullScreenPages.stack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            if (entry.dismissOnBackPress) {
                BackHandler { entry.onDismiss(); FullScreenPages.stack.remove(entry) }
            }
            entry.content()
        }
    }
}

/**
 * 兼容层：替代原来的 Dialog 调用。
 * 当 Composable 进入组合时自动显示全屏页面，离开时自动关闭。
 */
@Composable
fun FullScreenDialog(
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    content: @Composable () -> Unit
) {
    val id = remember { "fsd_${System.nanoTime()}" }

    DisposableEffect(id) {
        FullScreenPages.show(
            id = id,
            dismissOnBackPress = dismissOnBackPress,
            onDismiss = onDismissRequest,
            content = content
        )
        onDispose {
            FullScreenPages.dismiss(id)
        }
    }
}
