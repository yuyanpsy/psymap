package com.psymap.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PsyMapTheme {
                PsyMapApp()
            }
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
            background = Color(0xFFF5F5F5),
            error = Color(0xFFD32F2F),
            tertiary = Color(0xFF4CAF50),          // 绿色（正确）
            outline = Color(0xFFBDBDBD)
        ),
        typography = Typography(),
        content = content
    )
}
