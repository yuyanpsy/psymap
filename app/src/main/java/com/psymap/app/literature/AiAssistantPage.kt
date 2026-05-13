package com.psymap.app.literature

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AiAssistantPage(vm: LiteratureViewModel) {
    val context = LocalContext.current
    var selectedLit by remember { mutableStateOf<Literature?>(null) }
    var translateText by remember { mutableStateOf("") }
    var showTranslateInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AI 文献助手", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
        Text("选择一篇文献，使用AI辅助分析", fontSize = 13.sp, color = Color(0xFF888888))

        // 文献选择
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("选择文献", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                if (vm.literatures.isEmpty()) {
                    Text("暂无文献，请先在文献库中添加", fontSize = 12.sp, color = Color.Gray)
                } else {
                    vm.literatures.take(10).forEach { lit ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { selectedLit = lit }
                                .background(
                                    if (selectedLit?.id == lit.id) Color(0xFFFFF3E0) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedLit?.id == lit.id,
                                onClick = { selectedLit = lit },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF6C00))
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lit.title, fontSize = 13.sp, maxLines = 1, fontWeight = FontWeight.Medium)
                                if (lit.authors.isNotEmpty()) {
                                    Text(lit.authors.joinToString(", "), fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (vm.literatures.size > 10) {
                        Text("还有 ${vm.literatures.size - 10} 篇...", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 48.dp))
                    }
                }
            }
        }

        // AI 功能按钮
        if (selectedLit != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { vm.aiSummarize(selectedLit!!) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("摘要", fontSize = 13.sp)
                }
                Button(
                    onClick = { vm.aiFindRelated(selectedLit!!) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("相关文献", fontSize = 13.sp)
                }
            }

            OutlinedButton(
                onClick = { showTranslateInput = !showTranslateInput },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("翻译文本", fontSize = 13.sp)
            }

            if (showTranslateInput) {
                OutlinedTextField(
                    value = translateText,
                    onValueChange = { translateText = it },
                    label = { Text("输入要翻译的文本") },
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Button(
                    onClick = {
                        val text = translateText.ifBlank { selectedLit!!.abstract }
                        if (text.isNotBlank()) vm.aiTranslate(text)
                    },
                    enabled = translateText.isNotBlank() || selectedLit!!.abstract.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("开始翻译") }
            }
        }

        // AI 结果显示
        if (vm.isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFFEF6C00), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("AI 正在分析...", fontSize = 13.sp, color = Color(0xFF666666))
                }
            }
        }

        if (vm.aiResult.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI 结果", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF388E3C))
                        Row {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI结果", vm.aiResult))
                                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                            }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp), tint = Color(0xFF388E3C))
                            }
                            IconButton(onClick = { vm.clearAiResult() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(16.dp), tint = Color.Gray)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(vm.aiResult, fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF333333))
                    }
                }
            }
        }

        // 云同步
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color(0xFFF0F0F0))
        Spacer(Modifier.height(8.dp))
        Text("数据同步", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { vm.syncToCloud { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("推送到云端", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { vm.syncFromCloud { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("从云端恢复", fontSize = 12.sp)
            }
        }
    }
}
