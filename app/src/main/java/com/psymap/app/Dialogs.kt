package com.psymap.app

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

// ==================== 图片插入辅助 ====================

@Composable
fun RichTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 8,
    placeholder: String = ""
) {
    val context = LocalContext.current
    val imgDir = remember { java.io.File(context.getExternalFilesDir(null), "images").apply { mkdirs() } }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val fileName = "img_${System.currentTimeMillis()}.jpg"
                val destFile = java.io.File(imgDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }
                // 在文本末尾插入图片标记
                val imgTag = "\n![图片](${destFile.absolutePath})\n"
                onValueChange(value + imgTag)
            } catch (e: Exception) {
                Toast.makeText(context, "图片插入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = maxLines,
            placeholder = if (placeholder.isNotBlank()) {{ Text(placeholder) }} else null
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { imagePicker.launch("image/*") }) {
                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF1976D2))
                Spacer(Modifier.width(4.dp))
                Text("插入图片", fontSize = 12.sp, color = Color(0xFF1976D2))
            }
        }
    }
}

// ==================== 数字步进输入（+/- 按钮 + 可点击编辑） ====================

@Composable
fun NumberStepper(value: String, onValueChange: (String) -> Unit, min: Int = 1, max: Int = 999, suffix: String = "") {
    var editing by remember { mutableStateOf(false) }
    val focusReq = remember { FocusRequester() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.background(Color(0xFFF5F5F5), RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        IconButton(onClick = { val v = (value.toIntOrNull() ?: min); if (v > min) onValueChange("${v - 1}") },
            modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        Box(modifier = Modifier.width(44.dp).height(28.dp), contentAlignment = Alignment.Center) {
            if (editing) {
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = { newVal -> onValueChange(newVal.filter { c -> c.isDigit() }.take(3)) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusReq),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { editing = false }),
                    singleLine = true
                )
                LaunchedEffect(Unit) { focusReq.requestFocus() }
            } else {
                Text(
                    "$value$suffix", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { editing = true },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1
                )
            }
        }
        IconButton(onClick = { val v = (value.toIntOrNull() ?: min); if (v < max) onValueChange("${v + 1}") },
            modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}

// ==================== API 配置弹窗 ====================

@Composable
fun ApiConfigDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var key by remember { mutableStateOf(vm.apiKey) }
    var baseUrl by remember { mutableStateOf(vm.apiBaseUrl) }
    var model by remember { mutableStateOf(vm.modelName) }
    var aiOn by remember { mutableStateOf(vm.aiEnabled) }
    var tcId by remember { mutableStateOf(TencentConfig.secretId) }
    var tcKey by remember { mutableStateOf(TencentConfig.secretKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("AI 配置")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (aiOn) "AI已开启" else "AI已关闭", fontSize = 12.sp,
                        color = if (aiOn) Color(0xFF4CAF50) else Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Switch(checked = aiOn, onCheckedChange = { aiOn = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4CAF50)))
                }
            }
        },
        text = {
            Column {
                if (!aiOn) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier.fillMaxWidth()) {
                        Text("⚠️ AI功能已关闭，拍照OCR、心理学知识生成、AI学习计划等付费功能将不可用",
                            fontSize = 12.sp, color = Color(0xFFE65100),
                            modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = aiOn
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text("API Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = aiOn
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("OCR视觉模型") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = aiOn
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("腾讯云（TTS/OCR）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = tcId, onValueChange = { tcId = it },
                    label = { Text("SecretId") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = tcKey, onValueChange = { tcKey = it },
                    label = { Text("SecretKey") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.apiKey = key
                vm.apiBaseUrl = baseUrl
                vm.modelName = model
                vm.toggleAiEnabled(aiOn)
                vm.saveApiConfig()
                val context = vm.getApplication<android.app.Application>()
                TencentConfig.save(context.getSharedPreferences("psymap", android.content.Context.MODE_PRIVATE), tcId, tcKey)
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

    FullScreenDialog(onDismissRequest = { if (!importing) onDismiss() }) {
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
                                .clickable { if (!importing) selectedBankId = bank.id }
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
fun QuestionBankDetailSheet(vm: PsyMapViewModel, bankId: String, onDismiss: () -> Unit,
                            filterTypes: Set<QuestionType> = emptySet(),
                            filterFrequent: Boolean = false, filterMemorize: Boolean = false) {
    val bank = vm.questionBanks.find { it.id == bankId } ?: return
    val allQuestions = vm.getQuestionsForBank(bankId)
    val questions = allQuestions.filter { q ->
        (filterTypes.isEmpty() || q.type in filterTypes) &&
        (!filterFrequent || q.isFrequent) &&
        (!filterMemorize || q.isMemorize)
    }
    var showAddQuestion by remember { mutableStateOf(false) }
    var editingBankName by remember { mutableStateOf(false) }
    var newBankName by remember { mutableStateOf(bank.name) }

    // 多选模式
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var viewingQuestion by remember { mutableStateOf<Question?>(null) }

    FullScreenDialog(
        onDismissRequest = {
            if (isSelectMode) {
                isSelectMode = false
                selectedIds = emptySet()
            } else onDismiss()
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            shape = RoundedCornerShape(0.dp)
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
                    // 正常标题栏（高度与 TopAppBar 一致）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .height(64.dp)
                            .padding(horizontal = 16.dp),
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
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFFEF6C00))
                                if (vm.isAdmin) {
                                    IconButton(onClick = { editingBankName = true }) {
                                        Icon(Icons.Default.Edit, contentDescription = "编辑",
                                            modifier = Modifier.size(18.dp), tint = Color(0xFF333333))
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color(0xFF333333))
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
        // 使用筛选后的题目列表，确保上一题/下一题只在筛选结果中移动
        val filteredQuestions = allQuestions.filter { fq ->
            (filterTypes.isEmpty() || fq.type in filterTypes) &&
            (!filterFrequent || fq.isFrequent) &&
            (!filterMemorize || fq.isMemorize)
        }
        QuestionDetailDialog(
            question = q,
            vm = vm,
            onDismiss = { viewingQuestion = null },
            questionList = filteredQuestions,
            onNavigate = { viewingQuestion = it }
        )
    }
}

// ==================== 题目详情/编辑弹窗 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuestionDetailDialog(
    question: Question,
    vm: PsyMapViewModel,
    onDismiss: () -> Unit,
    questionList: List<Question> = emptyList(),
    onNavigate: ((Question) -> Unit)? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var editContent by remember(question.id) { mutableStateOf(question.content) }
    var editAnswer by remember(question.id) { mutableStateOf(question.answer) }
    var editExplanation by remember(question.id) { mutableStateOf(question.explanation) }
    var editOptions by remember(question.id) { mutableStateOf(question.options.joinToString("\n")) }
    val currentIndex = if (questionList.isNotEmpty()) questionList.indexOfFirst { it.id == question.id } else -1
    val bank = vm.questionBanks.find { it.id == question.bankId }
    val availableTypes = bank?.subject?.availableQuestionTypes() ?: QuestionType.entries.toList()
    // 从 vm 实时获取最新的 question 状态
    val liveQuestion = vm.questions.find { it.id == question.id } ?: question

    FullScreenDialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditing) "编辑题目" else "题目详情", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (vm.isAdmin && !isEditing) {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color(0xFFEF6C00), navigationIconContentColor = Color(0xFF333333), actionIconContentColor = Color(0xFF333333))
                )
            },
            bottomBar = {}
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 第一行：题目类型（可点击切换）
                Text("题目类型", fontSize = 11.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
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
                    val isChoiceType = liveQuestion.type == QuestionType.SINGLE_CHOICE || liveQuestion.type == QuestionType.MULTI_CHOICE

                    RichTextField(value = editContent, onValueChange = { editContent = it },
                        label = "题目内容", modifier = Modifier.fillMaxWidth(), maxLines = 8)
                    Spacer(Modifier.height(8.dp))
                    if (isChoiceType || editOptions.isNotBlank()) {
                        OutlinedTextField(value = editOptions, onValueChange = { editOptions = it },
                            label = { Text("选项（每行一个，如 A.选项内容）") }, modifier = Modifier.fillMaxWidth(), maxLines = 6,
                            placeholder = { Text("A.选项1\nB.选项2\nC.选项3\nD.选项4") })
                        Spacer(Modifier.height(8.dp))
                    }
                    RichTextField(value = editAnswer, onValueChange = { editAnswer = it },
                        label = if (isChoiceType) "正确答案（如 A 或 ABD）" else "答案", modifier = Modifier.fillMaxWidth(), maxLines = 8)

                    // 解析字段（所有题型可用）
                    Spacer(Modifier.height(8.dp))
                    RichTextField(value = editExplanation, onValueChange = { editExplanation = it },
                        label = "解析（选填）", modifier = Modifier.fillMaxWidth(), maxLines = 8,
                        placeholder = "知其然知其所以然...")

                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { isEditing = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            val opts = if (isChoiceType || editOptions.isNotBlank())
                                editOptions.lines().filter { it.isNotBlank() } else emptyList()
                            vm.updateQuestion(question.id, editContent, editAnswer, opts, editExplanation)
                            isEditing = false
                            onDismiss()
                        }) { Text("保存") }
                    }
                    Spacer(Modifier.height(48.dp))
                } else {
                    Text("题目", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    SimpleMarkdownText(liveQuestion.content)

                    if (liveQuestion.options.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("选项", fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(4.dp))
                        liveQuestion.options.forEachIndexed { i, opt ->
                            Text(text = renderInlineMarkdown("${('A' + i)}. $opt"), fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("答案", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            SimpleMarkdownText(liveQuestion.answer)
                        }
                    }

                    // 解析显示
                    if (liveQuestion.explanation.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text("💡 解析", fontSize = 12.sp, color = Color(0xFF7B1FA2))
                        Spacer(Modifier.height(4.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                SimpleMarkdownText(liveQuestion.explanation)
                            }
                        }
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

                // 上一题/下一题导航按钮
                if (questionList.size > 1 && onNavigate != null && !isEditing) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (currentIndex > 0) onNavigate(questionList[currentIndex - 1]) },
                            enabled = currentIndex > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("上一题")
                        }
                        Text("${currentIndex + 1}/${questionList.size}", fontSize = 13.sp, color = Color.Gray)
                        Button(
                            onClick = { if (currentIndex < questionList.size - 1) onNavigate(questionList[currentIndex + 1]) },
                            enabled = currentIndex < questionList.size - 1,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("下一题")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ==================== 添加题目弹窗 ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddQuestionDialog(bankId: String, vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var content by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var explanationText by remember { mutableStateOf("") }
    val bank = vm.questionBanks.find { it.id == bankId }
    val availableTypes = bank?.subject?.availableQuestionTypes() ?: QuestionType.entries
    var selectedType by remember { mutableStateOf(availableTypes.first()) }
    var optionsText by remember { mutableStateOf("") }
    var chapter by remember { mutableStateOf("") }
    var isFrequent by remember { mutableStateOf(false) }
    var isMemorize by remember { mutableStateOf(false) }

    FullScreenDialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("添加题目 - ${bank?.name ?: ""}", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val options = if (selectedType == QuestionType.SINGLE_CHOICE || selectedType == QuestionType.MULTI_CHOICE)
                                    optionsText.lines().filter { it.isNotBlank() } else emptyList()
                                vm.addQuestion(bankId, content, answer, selectedType, options, chapter, explanation = explanationText)
                                val lastQ = vm.questions.lastOrNull()
                                if (lastQ != null) {
                                    if (isFrequent) vm.toggleFrequent(lastQ.id)
                                    if (isMemorize) vm.toggleMemorize(lastQ.id)
                                }
                                onDismiss()
                            },
                            enabled = content.isNotBlank()
                        ) { Text("添加", fontWeight = FontWeight.Bold) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color(0xFFEF6C00), navigationIconContentColor = Color(0xFF333333), actionIconContentColor = Color(0xFF333333))
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text("题型", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
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

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = isFrequent, onClick = { isFrequent = !isFrequent },
                        label = { Text("🔥 常考", fontSize = 11.sp) })
                    FilterChip(selected = isMemorize, onClick = { isMemorize = !isMemorize },
                        label = { Text("📖 多背", fontSize = 11.sp) })
                }
                Spacer(Modifier.height(12.dp))

                RichTextField(value = content, onValueChange = { content = it },
                    label = "题目内容", modifier = Modifier.fillMaxWidth(), maxLines = 8)
                Spacer(Modifier.height(8.dp))

                if (selectedType == QuestionType.SINGLE_CHOICE || selectedType == QuestionType.MULTI_CHOICE) {
                    OutlinedTextField(value = optionsText, onValueChange = { optionsText = it },
                        label = { Text("选项（每行一个）") }, modifier = Modifier.fillMaxWidth(), maxLines = 8,
                        placeholder = { Text("A.选项1\nB.选项2\nC.选项3\nD.选项4") })
                    Spacer(Modifier.height(8.dp))
                }

                RichTextField(value = answer, onValueChange = { answer = it },
                    label = "答案", modifier = Modifier.fillMaxWidth(), maxLines = 8)
                Spacer(Modifier.height(8.dp))

                RichTextField(value = explanationText, onValueChange = { explanationText = it },
                    label = "解析（选填）", modifier = Modifier.fillMaxWidth(), maxLines = 8,
                    placeholder = "知其然知其所以然...")
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(value = chapter, onValueChange = { chapter = it },
                    label = { Text("章节（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
