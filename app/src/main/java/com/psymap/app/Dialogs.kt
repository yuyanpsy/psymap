package com.psymap.app

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ==================== API 配置弹窗 ====================

@Composable
fun ApiConfigDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var baseUrl by remember { mutableStateOf(vm.apiBaseUrl) }
    var model by remember { mutableStateOf(vm.modelName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 配置") },
        text = {
            Column {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("OCR视觉模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.apiKey = key
                vm.apiBaseUrl = baseUrl
                vm.modelName = model
                vm.saveApiConfig()
                onDismiss()
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ==================== 拍照导入弹窗 ====================

@Composable
fun PhotoImportDialog(vm: PsyMapViewModel, bitmap: Bitmap, onDismiss: () -> Unit) {
    var selectedBankId by remember { mutableStateOf(vm.questionBanks.firstOrNull()?.id ?: "") }
    var importing by remember { mutableStateOf(false) }
    val isLoading by vm.isLoading.collectAsState()
    val context = LocalContext.current

    // 监听导入结果
    LaunchedEffect(vm.importResultMessage) {
        if (vm.importResultMessage.isNotBlank() && importing) {
            Toast.makeText(context, vm.importResultMessage, Toast.LENGTH_LONG).show()
            importing = false
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = { if (!importing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("拍照录题", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { if (!importing) onDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "拍照预览",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                Spacer(Modifier.height(12.dp))

                Text("导入到题库:", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    vm.questionBanks.forEach { bank ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedBankId == bank.id,
                                onClick = { if (!importing) selectedBankId = bank.id }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${bank.subject.emoji} ${bank.name}", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (importing && isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("AI 正在识别题目...", fontSize = 14.sp, color = Color.Gray)
                    }
                    Spacer(Modifier.height(12.dp))
                }

                Button(
                    onClick = {
                        importing = true
                        vm.recognizeAndImport(bitmap, selectedBankId)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    enabled = selectedBankId.isNotBlank() && !importing
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (importing) "识别中..." else "AI 识别并导入")
                }
            }
        }
    }
}

// ==================== 题库详情弹窗 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestionBankDetailSheet(vm: PsyMapViewModel, bankId: String, onDismiss: () -> Unit) {
    val bank = vm.questionBanks.find { it.id == bankId } ?: return
    val questions = vm.getQuestionsForBank(bankId)
    var showAddQuestion by remember { mutableStateOf(false) }
    var editingBankName by remember { mutableStateOf(false) }
    var newBankName by remember { mutableStateOf(bank.name) }

    // 多选模式
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var viewingQuestion by remember { mutableStateOf<Question?>(null) }

    Dialog(
        onDismissRequest = {
            if (isSelectMode) {
                isSelectMode = false
                selectedIds = emptySet()
            } else onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题栏
                if (isSelectMode) {
                    // 多选模式标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                isSelectMode = false
                                selectedIds = emptySet()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "取消")
                            }
                            Text("已选 ${selectedIds.size} 题",
                                fontWeight = FontWeight.Medium, fontSize = 16.sp)
                        }
                        Row {
                            TextButton(onClick = {
                                selectedIds = if (selectedIds.size == questions.size)
                                    emptySet() else questions.map { it.id }.toSet()
                            }) {
                                Text(if (selectedIds.size == questions.size) "取消全选" else "全选")
                            }
                            Button(
                                onClick = { showDeleteConfirm = true },
                                enabled = selectedIds.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("删除")
                            }
                        }
                    }
                } else {
                    // 正常标题栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (editingBankName && vm.isAdmin) {
                            OutlinedTextField(
                                value = newBankName,
                                onValueChange = { newBankName = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        vm.renameBankIfAdmin(bankId, newBankName)
                                        editingBankName = false
                                    }) {
                                        Icon(Icons.Default.Check, contentDescription = "确认")
                                    }
                                }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${bank.subject.emoji} ${bank.name}",
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                if (vm.isAdmin) {
                                    IconButton(onClick = { editingBankName = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑",
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }

                // 统计信息
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("题数: ${questions.size}", fontSize = 13.sp, color = Color.Gray)
                    Text("错题: ${questions.count { it.isInWrongBook }}", fontSize = 13.sp, color = Color(0xFFD32F2F))
                    Text("收藏: ${questions.count { it.isInFavorites }}", fontSize = 13.sp, color = Color(0xFFFF9800))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // 题目列表
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    if (questions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无题目", color = Color.Gray)
                        }
                    }
                    questions.forEachIndexed { index, q ->
                        val isSelected = q.id in selectedIds
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectMode) {
                                            selectedIds = if (isSelected)
                                                selectedIds - q.id else selectedIds + q.id
                                        } else {
                                            viewingQuestion = q
                                        }
                                    },
                                    onLongClick = {
                                        if (vm.isAdmin && !isSelectMode) {
                                            isSelectMode = true
                                            selectedIds = setOf(q.id)
                                        }
                                    }
                                ),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFFFFE0B2) else Color(0xFFF5F5F5)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isSelectMode) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedIds = if (isSelected)
                                                selectedIds - q.id else selectedIds + q.id
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row {
                                        Text("${index + 1}. ", fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(q.content, fontSize = 14.sp, maxLines = 3)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(q.type.label, fontSize = 11.sp, color = Color.Gray)
                                        if (q.isFrequent) Text("🔥常考", fontSize = 11.sp, color = Color(0xFFEF6C00))
                                        if (q.isMemorize) Text("📖多背", fontSize = 11.sp, color = Color(0xFF1976D2))
                                        if (q.isInWrongBook) Text("📌错题", fontSize = 11.sp, color = Color(0xFFD32F2F))
                                        if (q.isInFavorites) Text("⭐收藏", fontSize = 11.sp, color = Color(0xFFFF9800))
                                    }
                                }
                            }
                        }
                    }
                }

                // 管理员添加题目按钮
                if (vm.isAdmin && !isSelectMode) {
                    Button(
                        onClick = { showAddQuestion = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("添加题目")
                    }
                }
            }
        }
    }

    // 删除确认弹窗
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 道题目吗？此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteQuestions(selectedIds)
                        selectedIds = emptySet()
                        isSelectMode = false
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showAddQuestion) {
        AddQuestionDialog(
            bankId = bankId,
            vm = vm,
            onDismiss = { showAddQuestion = false }
        )
    }

    viewingQuestion?.let { q ->
        QuestionDetailDialog(
            question = q,
            vm = vm,
            onDismiss = { viewingQuestion = null }
        )
    }
}

// ==================== 题目详情/编辑弹窗 ====================

@Composable
fun QuestionDetailDialog(question: Question, vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var editContent by remember(question.id) { mutableStateOf(question.content) }
    var editAnswer by remember(question.id) { mutableStateOf(question.answer) }
    var editOptions by remember(question.id) { mutableStateOf(question.options.joinToString("\n")) }
    val bank = vm.questionBanks.find { it.id == question.bankId }
    val availableTypes = bank?.subject?.availableQuestionTypes() ?: QuestionType.entries.toList()
    // 从 vm 实时获取最新的 question 状态
    val liveQuestion = vm.questions.find { it.id == question.id } ?: question

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isEditing) "编辑题目" else "题目详情", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (vm.isAdmin && !isEditing) {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 第一行：题目类型（可点击切换）
                Text("题目类型", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableTypes.forEach { type ->
                        FilterChip(
                            selected = liveQuestion.type == type,
                            onClick = { vm.updateQuestionType(question.id, type) },
                            label = { Text(type.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 第二行：标签（常考/多背 可切换，收藏/错题 只读）
                Text("标签", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = liveQuestion.isFrequent,
                        onClick = { vm.toggleFrequent(question.id) },
                        label = { Text("🔥 常考", fontSize = 11.sp) })
                    FilterChip(selected = liveQuestion.isMemorize,
                        onClick = { vm.toggleMemorize(question.id) },
                        label = { Text("📖 多背", fontSize = 11.sp) })
                    if (liveQuestion.isInFavorites) {
                        AssistChip(onClick = {}, label = { Text("⭐ 收藏", fontSize = 11.sp) })
                    }
                    if (liveQuestion.isInWrongBook) {
                        AssistChip(onClick = {}, label = { Text("📌 错题", fontSize = 11.sp) })
                    }
                }

                if (bank != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("所属题库: ${bank.subject.emoji} ${bank.name}", fontSize = 11.sp, color = Color.Gray)
                }

                Spacer(Modifier.height(12.dp))

                if (isEditing) {
                    OutlinedTextField(value = editContent, onValueChange = { editContent = it },
                        label = { Text("题目内容") }, modifier = Modifier.fillMaxWidth(), maxLines = 8)
                    Spacer(Modifier.height(8.dp))
                    if (liveQuestion.options.isNotEmpty()) {
                        OutlinedTextField(value = editOptions, onValueChange = { editOptions = it },
                            label = { Text("选项（每行一个）") }, modifier = Modifier.fillMaxWidth(), maxLines = 6)
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(value = editAnswer, onValueChange = { editAnswer = it },
                        label = { Text("答案") }, modifier = Modifier.fillMaxWidth(), maxLines = 8)
                } else {
                    Text("题目", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text(liveQuestion.content, fontSize = 15.sp, lineHeight = 22.sp)

                    if (liveQuestion.options.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("选项", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        liveQuestion.options.forEachIndexed { i, opt ->
                            Text("${('A' + i)}. $opt", fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("答案", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
                        Text(liveQuestion.answer, fontSize = 14.sp, modifier = Modifier.padding(12.dp), lineHeight = 20.sp)
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("复习 ${liveQuestion.reviewCount} 次", fontSize = 11.sp, color = Color.Gray)
                        Text("正确 ${liveQuestion.correctCount}", fontSize = 11.sp, color = Color(0xFF4CAF50))
                        Text("错误 ${liveQuestion.wrongCount}", fontSize = 11.sp, color = Color(0xFFD32F2F))
                        if (liveQuestion.correctCount + liveQuestion.wrongCount > 0) {
                            Text("错误率 ${(liveQuestion.errorRate * 100).toInt()}%", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isEditing) {
                Button(onClick = {
                    vm.updateQuestion(question.id, editContent, editAnswer)
                    isEditing = false
                    onDismiss()
                }) { Text("保存") }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {
            if (isEditing) {
                TextButton(onClick = { isEditing = false }) { Text("取消") }
            }
        }
    )
}

// ==================== 添加题目弹窗 ====================

@Composable
fun AddQuestionDialog(bankId: String, vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var content by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    val bank = vm.questionBanks.find { it.id == bankId }
    val availableTypes = bank?.subject?.availableQuestionTypes() ?: QuestionType.entries
    var selectedType by remember { mutableStateOf(availableTypes.first()) }
    var optionsText by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("") }
    var isFrequent by remember { mutableStateOf(false) }
    var isMemorize by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加题目 - ${bank?.name ?: ""}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // 题型选择（按科目过滤）
                Text("题型", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 标签
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = isFrequent, onClick = { isFrequent = !isFrequent },
                        label = { Text("🔥 常考", fontSize = 11.sp) })
                    FilterChip(selected = isMemorize, onClick = { isMemorize = !isMemorize },
                        label = { Text("📖 多背", fontSize = 11.sp) })
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(value = content, onValueChange = { content = it },
                    label = { Text("题目内容") }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
                Spacer(Modifier.height(8.dp))

                if (selectedType == QuestionType.SINGLE_CHOICE || selectedType == QuestionType.MULTI_CHOICE) {
                    OutlinedTextField(value = optionsText, onValueChange = { optionsText = it },
                        label = { Text("选项（每行一个）") }, modifier = Modifier.fillMaxWidth(), maxLines = 6)
                    Spacer(Modifier.height(8.dp))
                }

                OutlinedTextField(value = answer, onValueChange = { answer = it },
                    label = { Text("答案") }, modifier = Modifier.fillMaxWidth(), maxLines = 5)
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(value = chapter, onValueChange = { chapter = it },
                    label = { Text("章节（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val options = if (selectedType == QuestionType.SINGLE_CHOICE || selectedType == QuestionType.MULTI_CHOICE)
                        optionsText.lines().filter { it.isNotBlank() } else emptyList()
                    vm.addQuestion(bankId, content, answer, selectedType, options, chapter)
                    // 设置标签
                    val lastQ = vm.questions.lastOrNull()
                    if (lastQ != null) {
                        if (isFrequent) vm.toggleFrequent(lastQ.id)
                        if (isMemorize) vm.toggleMemorize(lastQ.id)
                    }
                    onDismiss()
                },
                enabled = content.isNotBlank() && answer.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
