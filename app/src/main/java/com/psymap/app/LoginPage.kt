package com.psymap.app

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginPage(vm: PsyMapViewModel) {
    var guestName by remember { mutableStateOf("") }
    var showGuestDialog by remember { mutableStateOf(false) }
    var wechatLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 初始化微信 SDK
    LaunchedEffect(Unit) {
        WeChatLogin.init(context)
    }

    // 监听微信登录回调
    LaunchedEffect(Unit) {
        com.psymap.app.wxapi.WXEntryActivity.onLoginResult = { code, errCode ->
            if (code != null && errCode == 0) {
                wechatLoading = true
                WeChatLogin.getAccessToken(code,
                    onResult = { nickname, openId, avatarUrl ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            vm.loginAsNormalUser(nickname, openId)
                            wechatLoading = false
                        }
                    },
                    onError = { error ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            Toast.makeText(context, "微信登录失败: $error", Toast.LENGTH_SHORT).show()
                            wechatLoading = false
                        }
                    }
                )
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "微信授权取消", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFEF6C00), Color(0xFFFF9800), Color(0xFFFFE0B2))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = null,
                modifier = Modifier.size(100.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(16.dp))
            Text("羽言心理", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(
                "新知 · 成长 · 快乐",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(48.dp))

            Button(
                onClick = {
                    if (WeChatLogin.isWeChatInstalled()) {
                        WeChatLogin.login()
                    } else {
                        showGuestDialog = true  // 微信未安装，用手动输入
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF07C160)),
                enabled = !wechatLoading
            ) {
                if (wechatLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (wechatLoading) "登录中..." else "微信登录", fontSize = 16.sp)
            }

            Spacer(Modifier.height(32.dp))
            Text(
                "距离考研还有 ${vm.daysUntilExam} 天",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }

    // 普通用户登录（模拟微信）
    if (showGuestDialog) {
        AlertDialog(
            onDismissRequest = { showGuestDialog = false },
            title = { Text("输入昵称") },
            text = {
                Column {
                    Text("模拟微信授权登录", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = guestName,
                        onValueChange = { guestName = it },
                        label = { Text("昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.loginAsNormalUser(guestName.ifBlank { "考研人" })
                        showGuestDialog = false
                    }
                ) {
                    Text("进入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuestDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
