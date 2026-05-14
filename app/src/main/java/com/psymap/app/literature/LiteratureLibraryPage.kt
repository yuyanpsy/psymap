package com.psymap.app.literature

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psymap.app.FullScreenDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiteratureLibraryPage(vm: LiteratureViewModel, onOpenPdf: (Literature) -> Unit) {
    val context = LocalContext.current
    var showDoiImport by remember { mutableStateOf(false) }
    var showGroupManager by remember { mutableStateOf(false) }
    var showDuplicates by remember { mutableStateOf(false) }
    var showOnlineSearch by remember { mutableStateOf(false) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var showDetail by remember { mutableStateOf<Literature?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Literature?>(null) }

    // 多选PDF导入
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri -> vm.importFromPdf(context, uri) }
            Toast.makeText(context, "正在导入 ${uris.size} 篇文献...", Toast.LENGTH_SHORT).show()
        }
    }

    // 筛选
    val filteredLiteratures = if (selectedGroupId != null) {
        vm.literatures.filter { it.groupId == selectedGroupId }
    } else if (vm.searchQuery.isNotBlank()) {
        vm.searchResults
    } else {
        vm.literatures
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索栏 + 操作按钮整合
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // 搜索输入
                OutlinedTextField(
                    value = vm.searchQuery,
                    onValueChange = { vm.search(it) },
                    placeholder = { Text("搜索标题/作者/关键词...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (vm.searchQuery.isNotBlank()) {
                            IconButton(onClick = { vm.search("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFE8E8E8),
                        focusedBorderColor = Color(0xFFEF6C00)
                    )
                )
                Spacer(Modifier.height(10.dp))
                // 操作按钮行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(icon = Icons.Default.PictureAsPdf, label = "导入PDF") {
                        pdfPicker.launch(arrayOf("application/pdf"))
                    }
                    ActionButton(icon = Icons.Default.TravelExplore, label = "在线搜索") {
                        showOnlineSearch = true
                    }
                    ActionButton(icon = Icons.Default.Link, label = "DOI导入") {
                        showDoiImport = true
                    }
                    ActionButton(icon = Icons.Default.FindReplace, label = "去重") {
                        showDuplicates = true
                    }
                }
            }
        }

        // 分组标签
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selectedGroupId == null,
                onClick = { selectedGroupId = null },
                label = { Text("全部(${vm.literatures.size})", fontSize = 12.sp) },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFEF6C00),
                    selectedLabelColor = Color.White
                )
            )
            vm.groups.forEach { group ->
                val count = vm.literatures.count { it.groupId == group.id }
                FilterChip(
                    selected = selectedGroupId == group.id,
                    onClick = { selectedGroupId = if (selectedGroupId == group.id) null else group.id },
                    label = { Text("${group.name}($count)", fontSize = 12.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEF6C00),
                        selectedLabelColor = Color.White
                    )
                )
            }
            IconButton(onClick = { showGroupManager = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Settings, contentDescription = "管理分组", modifier = Modifier.size(20.dp), tint = Color(0xFFEF6C00))
            }
        }

        // 文献列表
        if (vm.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFEF6C00))
            }
        } else if (filteredLiteratures.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFFCCCCCC))
                    Spacer(Modifier.height(12.dp))
                    Text("暂无文献", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("导入PDF、在线搜索或DOI导入", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredLiteratures, key = { it.id }) { lit ->
                    LiteratureItem(
                        lit = lit,
                        onClick = { showDetail = lit },
                        onOpenPdf = { if (lit.pdfPath.isNotBlank()) onOpenPdf(lit) }
                    )
                }
            }
        }
    }

    // DOI 导入
    if (showDoiImport) {
        DoiImportDialog(vm = vm, onDismiss = { showDoiImport = false })
    }

    // 在线搜索（全屏）
    if (showOnlineSearch) {
        FullScreenDialog(onDismissRequest = { showOnlineSearch = false }) {
            OnlineSearchPage(vm = vm, onBack = { showOnlineSearch = false })
        }
    }

    // 分组管理
    if (showGroupManager) {
        GroupManagerDialog(vm = vm, onDismiss = { showGroupManager = false })
    }

    // 去重
    if (showDuplicates) {
        DuplicatesDialog(vm = vm, onDismiss = { showDuplicates = false })
    }

    // 文献详情（全屏）
    showDetail?.let { lit ->
        FullScreenDialog(onDismissRequest = { showDetail = null }) {
            LiteratureDetailPage(
                lit = lit, vm = vm,
                onBack = { showDetail = null },
                onOpenPdf = { onOpenPdf(lit); showDetail = null },
                onDelete = { showDeleteConfirm = lit; showDetail = null }
            )
        }
    }

    // 删除确认
    showDeleteConfirm?.let { lit ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${lit.title}」吗？\n此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = { vm.deleteLiterature(lit.id); showDeleteConfirm = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun LiteratureItem(lit: Literature, onClick: () -> Unit, onOpenPdf: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(lit.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (lit.authors.isNotEmpty()) {
                    Text(lit.authors.joinToString(", "), fontSize = 12.sp, color = Color(0xFF666666), maxLines = 1, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
                }
                if (lit.year > 0) {
                    Text("${lit.year}", fontSize = 12.sp, color = Color(0xFFEF6C00), fontWeight = FontWeight.Medium)
                }
            }
            if (lit.journal.isNotBlank()) {
                Text(lit.journal, fontSize = 11.sp, color = Color(0xFF999999), maxLines = 1)
            }
            if (lit.tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    lit.tags.take(4).forEach { tag ->
                        Box(modifier = Modifier.background(Color(0xFFFFF3E0), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(tag, fontSize = 10.sp, color = Color(0xFFEF6C00))
                        }
                    }
                }
            }
            if (lit.pdfPath.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onOpenPdf() }) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFD32F2F))
                    Spacer(Modifier.width(4.dp))
                    Text("打开PDF", fontSize = 11.sp, color = Color(0xFFD32F2F))
                }
            }
        }
    }
}

// ==================== DOI 导入 ====================
@Composable
private fun DoiImportDialog(vm: LiteratureViewModel, onDismiss: () -> Unit) {
    var doi by remember { mutableStateOf("") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("DOI 导入", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("输入DOI，自动从CrossRef获取文献元数据", fontSize = 12.sp, color = Color(0xFF888888))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = doi, onValueChange = { doi = it },
                    label = { Text("DOI") },
                    placeholder = { Text("例: 10.1037/rev0000126") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("支持格式: 10.xxxx/xxx 或 DOI:10.xxxx/xxx", fontSize = 11.sp, color = Color(0xFFAAAAAA))
                if (vm.doiImportError.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(vm.doiImportError, fontSize = 12.sp, color = Color(0xFFD32F2F))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (doi.isNotBlank()) {
                        vm.importFromDoi(doi.trim())
                        Toast.makeText(context, "正在获取文献信息...", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                enabled = doi.isNotBlank() && !vm.isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                shape = RoundedCornerShape(8.dp)
            ) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Color(0xFF999999)) } }
    )
}

// ==================== 分组管理 ====================
@Composable
private fun GroupManagerDialog(vm: LiteratureViewModel, onDismiss: () -> Unit) {
    var newGroupName by remember { mutableStateOf("") }
    var editingGroupId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理分组", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newGroupName, onValueChange = { newGroupName = it },
                        placeholder = { Text("新分组名称") }, singleLine = true,
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)
                    )
                    Button(
                        onClick = { if (newGroupName.isNotBlank()) { vm.createGroup(newGroupName); newGroupName = "" } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("添加") }
                }
                Spacer(Modifier.height(12.dp))
                vm.groups.forEach { group ->
                    if (editingGroupId == group.id) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = editingName, onValueChange = { editingName = it },
                                singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp)
                            )
                            IconButton(onClick = { vm.renameGroup(group.id, editingName); editingGroupId = null }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Check, contentDescription = "确认", modifier = Modifier.size(16.dp), tint = Color(0xFF4CAF50))
                            }
                            IconButton(onClick = { editingGroupId = null }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "取消", modifier = Modifier.size(16.dp), tint = Color.Gray)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(group.name, modifier = Modifier.weight(1f), fontSize = 14.sp)
                            Text("${vm.literatures.count { it.groupId == group.id }}篇", fontSize = 12.sp, color = Color.Gray)
                            IconButton(onClick = { editingGroupId = group.id; editingName = group.name }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "改名", modifier = Modifier.size(16.dp), tint = Color(0xFFEF6C00))
                            }
                            IconButton(onClick = { vm.deleteGroup(group.id) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", modifier = Modifier.size(16.dp), tint = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
                if (vm.groups.isEmpty()) {
                    Text("暂无分组，请先创建", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

// ==================== 去重对话框 ====================
@Composable
private fun DuplicatesDialog(vm: LiteratureViewModel, onDismiss: () -> Unit) {
    val duplicates = remember { vm.findDuplicates() }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重复检测", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (duplicates.isEmpty()) {
                    Text("未发现重复文献 ✓", color = Color(0xFF4CAF50), fontSize = 14.sp)
                } else {
                    Text("发现 ${duplicates.size} 组疑似重复:", fontSize = 13.sp, color = Color(0xFFEF6C00))
                    Spacer(Modifier.height(8.dp))
                    duplicates.take(10).forEach { (a, b) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp).background(Color(0xFFFAFAFA), RoundedCornerShape(6.dp)).padding(8.dp)) {
                            Text("① ${a.title}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("② ${b.title}", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = {
                                vm.deleteLiterature(b.id)
                                Toast.makeText(context, "已删除重复项", Toast.LENGTH_SHORT).show()
                            }) { Text("删除②", fontSize = 11.sp, color = Color(0xFFD32F2F)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ==================== 文献详情（全屏） ====================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LiteratureDetailPage(
    lit: Literature, vm: LiteratureViewModel,
    onBack: () -> Unit, onOpenPdf: () -> Unit, onDelete: () -> Unit
) {
    var newTag by remember { mutableStateOf("") }
    var showMoveGroup by remember { mutableStateOf(false) }
    // 实时获取最新数据
    val currentLit = vm.literatures.find { it.id == lit.id } ?: lit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文献详情", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } },
                actions = {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFD32F2F)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color(0xFFEF6C00))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 标题
            Text(currentLit.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp)

            // 元数据
            if (currentLit.authors.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF888888))
                    Spacer(Modifier.width(8.dp))
                    Text(currentLit.authors.joinToString(", "), fontSize = 14.sp, color = Color(0xFF555555))
                }
            }
            if (currentLit.journal.isNotBlank() || currentLit.year > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF888888))
                    Spacer(Modifier.width(8.dp))
                    if (currentLit.journal.isNotBlank()) Text(currentLit.journal, fontSize = 14.sp, color = Color(0xFF555555))
                    if (currentLit.year > 0) { Spacer(Modifier.width(8.dp)); Text("(${currentLit.year})", fontSize = 14.sp, color = Color(0xFFEF6C00)) }
                }
            }
            if (currentLit.doi.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF1976D2))
                    Spacer(Modifier.width(8.dp))
                    Text("DOI: ${currentLit.doi}", fontSize = 13.sp, color = Color(0xFF1976D2))
                }
            }

            // 摘要
            if (currentLit.abstract.isNotBlank()) {
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Text("摘要", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(currentLit.abstract, fontSize = 13.sp, color = Color(0xFF444444), lineHeight = 20.sp)
                }
            }

            // 标签管理
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Text("标签", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                currentLit.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { vm.removeTag(currentLit.id, tag) },
                        label = { Text(tag, fontSize = 12.sp) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTag, onValueChange = { newTag = it },
                    placeholder = { Text("输入新标签") }, singleLine = true,
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)
                )
                Button(
                    onClick = { if (newTag.isNotBlank()) { vm.addTag(currentLit.id, newTag.trim()); newTag = "" } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("添加") }
            }

            // 分组
            HorizontalDivider(color = Color(0xFFF0F0F0))
            Text("分组", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            val currentGroup = vm.groups.find { it.id == currentLit.groupId }
            if (currentGroup != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFEF6C00))
                    Spacer(Modifier.width(8.dp))
                    Text(currentGroup.name, fontSize = 14.sp, color = Color(0xFFEF6C00))
                }
            } else {
                Text("未分组", fontSize = 13.sp, color = Color.Gray)
            }
            // 分组选择
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = currentLit.groupId.isBlank(),
                    onClick = { vm.moveToGroup(currentLit.id, "") },
                    label = { Text("无分组", fontSize = 12.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFEF6C00), selectedLabelColor = Color.White)
                )
                vm.groups.forEach { group ->
                    FilterChip(
                        selected = currentLit.groupId == group.id,
                        onClick = { vm.moveToGroup(currentLit.id, group.id) },
                        label = { Text(group.name, fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFEF6C00), selectedLabelColor = Color.White)
                    )
                }
            }

            // 操作按钮
            Spacer(Modifier.height(8.dp))
            if (currentLit.pdfPath.isNotBlank()) {
                Button(
                    onClick = onOpenPdf,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF6C00)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("阅读PDF")
                }
            }
        }
    }
}

// ==================== 操作按钮组件 ====================
@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 8.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = Color(0xFFEF6C00))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color(0xFF555555))
    }
}
