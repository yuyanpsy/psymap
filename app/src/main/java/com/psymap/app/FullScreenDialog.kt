package com.psymap.app

import android.app.Dialog
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties

/**
 * 全屏 Dialog，强制设置 status bar 透明。
 */
@Composable
fun FullScreenDialog(
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissOnBackPress,
            decorFitsSystemWindows = false
        )
    ) {
        // 获取 Dialog 的 Window 并设置 status bar 透明
        val view = LocalView.current
        LaunchedEffect(Unit) {
            try {
                // Compose Dialog 的 view hierarchy: ComposeView -> DialogLayout -> DecorView
                // 通过 view.context 获取 DialogWrapper，再获取 Window
                val dialogWindow = findDialogWindow(view)
                dialogWindow?.let { window ->
                    window.statusBarColor = android.graphics.Color.TRANSPARENT
                    window.navigationBarColor = android.graphics.Color.WHITE
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            } catch (_: Exception) {}
        }
        content()
    }
}

/** 从 Compose Dialog 内部的 View 向上查找 Dialog 的 Window */
private fun findDialogWindow(view: android.view.View): Window? {
    // 方法1: 通过 view.context 查找
    var ctx = view.context
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) {
            // 这是 Activity 的 window，不是 Dialog 的
            break
        }
        ctx = ctx.baseContext
    }
    // 方法2: 通过 rootView 的 LayoutParams 获取 Window token，再找 Dialog
    try {
        val rootView = view.rootView
        val lp = rootView.layoutParams
        if (lp is WindowManager.LayoutParams) {
            // 直接修改 LayoutParams 的 flags
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS.inv()
            val wm = view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
            wm.updateViewLayout(rootView, lp)
        }
    } catch (_: Exception) {}
    return null
}

/**
 * 全局覆盖层管理器（备用方案）。
 */
object OverlayManager {
    var openOverlay: ((@Composable () -> Unit) -> Unit)? = null
    var closeOverlay: (() -> Unit)? = null

    fun open(content: @Composable () -> Unit) {
        openOverlay?.invoke(content)
    }

    fun close() {
        closeOverlay?.invoke()
    }
}
