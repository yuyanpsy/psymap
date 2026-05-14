package com.psymap.app.literature

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AiAssistantPage(vm: LiteratureViewModel) {
    val context = LocalContext.current
    var selectedLit by remember { mutableStateOf<Literature?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 选择文献
        item {
            Text("选择文献", fontSize = 13.sp, color = Color(0xFF888888))
        }

        items(vm.literatures) { lit ->
            val isSelected = selectedLit?.id == lit.id
            Card(
                modifier = Modifier.fillMaxWidth().clickable { selectedLit = lit },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFFFFF3E0) else Color.White
                ),
                elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { selectedLit = lit },
                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF6C00)),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lit.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (lit.authors.isNotEmpty()) {
                            Text(lit.authors.take(2).joinToString(", "), fontSize = 11.sp, color = Color(0xFF888888), maxLines = 1)
                        }
                    }
                    if (lit.year > 0) {
                        Text("${lit.year}", fontSize = 11.sp, color = Color(0xFFEF6C00))
                    }
                }
            }
        }

        // 功能按钮（选中文献后显示）
        if (selectedLit != null) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // 摘要提取
                    Button(
                        onClick = { vm.extractSummary(selectedLit!!) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Summarize, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("提取摘要", fontSize = 13.sp)
                    }
                    // 相关文献
                    Button(
                        onClick = { vm.aiFindRelated(selectedLit!!) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("相关文献", fontSize = 13.sp)
                    }
                }
            }
        }

        // 加载状态
        if (vm.isLoading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFFEF6C00), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("正在处理...", fontSize = 13.sp, color = Color(0xFF666666))
                    }
                }
            }
        }

        // AI 结果
        if (vm.aiResult.isNotBlank()) {
            item {
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
                            Text("结果", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF388E3C))
                            Row {
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("结果", vm.aiResult))
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
        }

        // 云同步
        item {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Spacer(Modifier.height(8.dp))
            Text("数据同步", fontSize = 13.sp, color = Color(0xFF888888))
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.syncToCloud { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("推送云端", fontSize = 12.sp)
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
}
