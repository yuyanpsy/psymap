package com.psymap.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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
            // 先从栈中移除，再调用 onDismiss 回调
            // 这样 onDismiss 触发的 DisposableEffect.onDispose 中的 dismiss(id) 不会重复操作
            stack.removeAt(stack.size - 1)
            top.onDismiss()
        }
    }
}

/**
 * 在 PsyMapApp 中调用，渲染全屏页面栈。
 */
@Composable
fun RenderFullScreenPages() {
    val hasPages = FullScreenPages.stack.isNotEmpty()

    // 统一的 BackHandler：只要栈非空就拦截系统返回手势
    // 放在页面渲染之外，确保不会因为页面移除导致短暂的拦截空隙
    BackHandler(enabled = hasPages) {
        FullScreenPages.dismissTop()
    }

    for (entry in FullScreenPages.stack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .statusBarsPadding()
        ) {
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
