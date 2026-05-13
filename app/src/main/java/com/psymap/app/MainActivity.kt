package com.psymap.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        super.onCreate(savedInstanceState)

        // 监听 App 进入后台，触发数据同步
        val vm = ViewModelProvider(this)[PsyMapViewModel::class.java]
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                vm.onAppBackground()
            }
        })

        // 处理外部分享的 Intent
        val sharedData = handleShareIntent(intent)

        setContent {
            PsyMapTheme {
                PsyMapApp(sharedImageUris = sharedData.imageUris, sharedFileUri = sharedData.fileUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // 新 Intent 到来时重新创建 UI（处理分享）
        val sharedData = handleShareIntent(intent)
        if (sharedData.imageUris.isNotEmpty() || sharedData.fileUri != null) {
            // 用新的 key 强制重建 Composable
            setContent {
                PsyMapTheme {
                    key(System.currentTimeMillis()) {
                        PsyMapApp(sharedImageUris = sharedData.imageUris, sharedFileUri = sharedData.fileUri)
                    }
                }
            }
        }
    }

    private data class SharedData(val imageUris: List<android.net.Uri> = emptyList(), val fileUri: android.net.Uri? = null)

    private fun handleShareIntent(intent: Intent?): SharedData {
        if (intent == null) return SharedData()
        val action = intent.action
        val type = intent.type ?: ""

        return when {
            action == Intent.ACTION_SEND && type.startsWith("image/") -> {
                val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                SharedData(imageUris = listOfNotNull(uri))
            }
            action == Intent.ACTION_SEND_MULTIPLE && type.startsWith("image/") -> {
                val uris = intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: emptyList()
                SharedData(imageUris = uris)
            }
            action == Intent.ACTION_SEND && (type.contains("pdf") || type.startsWith("text/") || type.contains("document") || type.contains("msword") || type.contains("ms-excel")) -> {
                val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                SharedData(fileUri = uri)
            }
            else -> SharedData()
        }
    }
}

// 暖橙色主题，参考截图风格
@Composable
fun PsyMapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFEF6C00),         // 橙色主色
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE0B2),
            onPrimaryContainer = Color(0xFFE65100),
            secondary = Color(0xFF795548),         // 棕色辅助
            secondaryContainer = Color(0xFFD7CCC8),
            surface = Color.White,
            background = Color(0xFFFAFAFA),
            error = Color(0xFFD32F2F),
            tertiary = Color(0xFF4CAF50),          // 绿色（正确）
            outline = Color(0xFFBDBDBD)
        ),
        typography = Typography(),
        content = content
    )
}
