package com.psymap.app.literature

import android.widget.Toast
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineSearchPage(vm: LiteratureViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("relevance") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("在线文献搜索", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color(0xFFEF6C00))
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // 搜索栏
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("搜索关键词...", fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清除", modifier = Modifier.size(18.dp), tint = Color.Gray)
                            }
                        }
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                        focusedBorderColor = Color(0xFFEF6C00)
                    )
                )
                Button(
                    onClick = { if (query.isNotBlank()) vm.searchOnline(query.trim(), sortBy) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                    shape = RoundedCornerShape(8.dp),
                    enabled = query.isNotBlank() && !vm.isLoading,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("搜索", fontSize = 13.sp) }
            }

            // 排序选项
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("排序:", fontSize = 12.sp, color = Color(0xFF888888))
                listOf("relevance" to "相关度", "date_desc" to "最新", "date_asc" to "最早", "cited" to "引用量").forEach { (key, label) ->
                    FilterChip(
                        selected = sortBy == key,
                        onClick = { sortBy = key; if (query.isNotBlank()) vm.searchOnline(query.trim(), key) },
                        label = { Text(label, fontSize = 11.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFEF6C00),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFFF5F5F5)
                        )
                    )
                }
            }

            // 提示
            Text(
                "数据源: OpenAlex · 多关键词空格分隔",
                fontSize = 10.sp, color = Color(0xFFAAAAAA),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(color = Color(0xFFF0F0F0))

            // 搜索结果
            if (vm.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFEF6C00))
                        Spacer(Modifier.height(8.dp))
                        Text("正在搜索...", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    if (vm.onlineSearchResults.isEmpty() && query.isNotBlank()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                                Text("未找到结果，请尝试其他关键词", color = Color.Gray)
                            }
                        }
                    }
                    items(vm.onlineSearchResults) { result ->
                        OnlineResultItem(
                            result = result,
                            onAdd = {
                                vm.addLiterature(result)
                                Toast.makeText(context, "已添加: ${result.title.take(30)}", Toast.LENGTH_SHORT).show()
                            },
                            onDownload = {
                                if (result.doi.isNotBlank()) {
                                    vm.downloadPdf(context, result)
                                    Toast.makeText(context, "正在尝试下载PDF...", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "无DOI，无法下载", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineResultItem(result: Literature, onAdd: () -> Unit, onDownload: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 44.dp)) {
                Text(result.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                if (result.authors.isNotEmpty()) {
                    Text(result.authors.take(3).joinToString(", ") + if (result.authors.size > 3) " 等" else "",
                        fontSize = 11.sp, color = Color(0xFF666666), maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (result.journal.isNotBlank()) {
                        Text(result.journal, fontSize = 10.sp, color = Color(0xFF999999), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (result.year > 0) {
                        Text("${result.year}", fontSize = 11.sp, color = Color(0xFFEF6C00), fontWeight = FontWeight.Medium)
                    }
                }
            }
            // 右侧操作按钮
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onAdd, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.AddCircle, contentDescription = "添加", tint = Color(0xFFEF6C00), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDownload, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "下载PDF", tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
