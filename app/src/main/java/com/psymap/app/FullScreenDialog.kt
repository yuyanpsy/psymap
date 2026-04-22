package com.psymap.app

import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 全屏 Dialog，自动设置 status bar 为透明。
 * 替代所有 Dialog(properties = DialogProperties(usePlatformDefaultWidth = false)) 的用法。
 */
@Composable
fun FullScreenDialog(
    onDismissRequest: () -> Unit,
    dismissOnBackPress: Boolean = true,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissOnBackPress
        )
    ) {
        // 设置 Dialog window 的 status bar 为透明
        val view = LocalView.current
        LaunchedEffect(Unit) {
            try {
                val window = (view.parent as? android.view.View)?.rootView?.let { rootView ->
                    val lp = rootView.layoutParams
                    if (lp is WindowManager.LayoutParams) {
                        val wm = view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                        // 设置 status bar 透明
                        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS.inv()
                        wm.updateViewLayout(rootView, lp)
                        rootView
                    } else null
                }
                // 通过反射获取 Dialog 的 Window 对象设置 statusBarColor
                val dialogWindow = view.context.let { ctx ->
                    // Compose Dialog 内部的 context 是 ContextThemeWrapper
                    try {
                        val field = ctx.javaClass.getDeclaredField("mDialog")
                        field.isAccessible = true
                        val dialog = field.get(ctx) as? android.app.Dialog
                        dialog?.window
                    } catch (_: Exception) {
                        null
                    }
                }
                dialogWindow?.let { w ->
                    w.statusBarColor = android.graphics.Color.TRANSPARENT
                    w.decorView.systemUiVisibility = w.decorView.systemUiVisibility or
                        android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            } catch (_: Exception) {}
        }
        content()
    }
}
