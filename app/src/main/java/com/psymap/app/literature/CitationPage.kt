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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitationPage(vm: LiteratureViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedStyle by remember { mutableStateOf(CitationStyle.GB_T7714) }
    var selectedLits by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        topBar = {
            LitTopBar(title = "引用管理", onBack = onBack)
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 引用格式选择
            Text("引用格式", fontSize = 13.sp, color = Color(0xFF666666), fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CitationStyle.entries.take(4).forEach { style ->
                    FilterChip(
                        selected = selectedStyle == style,
                        onClick = { selectedStyle = style },
                        label = { Text(style.label, fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEF6C00),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CitationStyle.entries.drop(4).forEach { style ->
                    FilterChip(
                        selected = selectedStyle == style,
                        onClick = { selectedStyle = style },
                        label = { Text(style.label, fontSize = 11.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEF6C00),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF0F0F0))

            // 批量操作
            if (selectedLits.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val citations = selectedLits.mapNotNull { id ->
                                vm.literatures.find { it.id == id }
                            }.mapIndexed { idx, lit ->
                                "[${idx + 1}] ${vm.generateCitation(lit, selectedStyle)}"
                            }.joinToString("\n\n")
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("引用", citations))
                            Toast.makeText(context, "已复制 ${selectedLits.size} 条引用", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("复制选中(${selectedLits.size})", fontSize = 13.sp)
                    }
                    TextButton(onClick = { selectedLits = emptySet() }) {
                        Text("取消选择", fontSize = 13.sp)
                    }
                }
            }

            // 文献列表 + 引用预览
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(vm.literatures, key = { it.id }) { lit ->
                    val citation = vm.generateCitation(lit, selectedStyle)
                    val isSelected = lit.id in selectedLits

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable {
                                selectedLits = if (isSelected) selectedLits - lit.id else selectedLits + lit.id
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFFFFF3E0) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedLits = if (isSelected) selectedLits - lit.id else selectedLits + lit.id
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFEF6C00)),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(lit.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("引用", citation))
                                        Toast.makeText(context, "已复制引用", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "复制", modifier = Modifier.size(16.dp), tint = Color(0xFFEF6C00))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                citation, fontSize = 12.sp, color = Color(0xFF666666),
                                lineHeight = 18.sp, maxLines = 3
                            )
                        }
                    }
                }

                if (vm.literatures.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            Text("暂无文献，请先添加文献", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
