package com.psymap.app

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomePage(vm: PsyMapViewModel) {
    var searchText by remember { mutableStateOf("") }
    var showBankDetail by remember { mutableStateOf(false) }
    var selectedBankId by remember { mutableStateOf("") }
    var showWrongBookDialog by remember { mutableStateOf(false) }
    var showFavoritesDialog by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showCreateBank by remember { mutableStateOf(false) }
    var showMakeAudio by remember { mutableStateOf(false) }
    var showListenAudio by remember { mutableStateOf(false) }
    var showExamSetup by remember { mutableStateOf(false) }
    var pendingFileUri by remember { mutableStateOf<Uri?>(null) }
    var showFileImportDialog by remember { mutableStateOf(false) }
    var showStudyPlan by remember { mutableStateOf(false) }
    var clickedQuestion by remember { mutableStateOf<Question?>(null) }
    var showMindMap by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 拍照搜题
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        if (bitmap != null && vm.isAdmin) {
            val bankId = vm.questionBanks.firstOrNull()?.id ?: ""
            if (bankId.isNotBlank()) vm.recognizeAndImport(bitmap, bankId)
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
        else Toast.makeText(context, "需要相机权限", Toast.LENGTH_SHORT).show()
    }

    // 文件选择 — 只保存 URI，弹出对话框让用户选题库和题型
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            pendingFileUri = uri
            showFileImportDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 搜索栏
        item {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it; vm.searchQuestions(it) },
                placeholder = { Text("搜索题目 / 题库 / 关键词", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = ""; vm.searchQuestions("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        // 倒计时卡片
        item { CountdownCard(vm) }

        // 快捷功能入口
        item {
            QuickActions(
                onStudyPlan = { showStudyPlan = true },
                onCalendar = { showCalendar = true },
                onWrongBook = { showWrongBookDialog = true },
                onFavorites = { showFavoritesDialog = true },
                onMindMap = { showMindMap = true },
                onMakeAudio = { showMakeAudio = true },
                onListen = { showListenAudio = true },
                onMoreKnowledge = { showExamSetup = true }
            )
        }

        // 搜索结果
        if (searchText.isNotEmpty() && vm.searchResults.isNotEmpty()) {
            item { Text("搜索结果 (${vm.searchResults.size})", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Medium) }
            items(vm.searchResults.take(20)) { question -> SearchResultItem(question, vm) { clickedQuestion = question } }
        } else {
            // 题库列表标题栏 + 导入文件/新建题库入口
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("我的题库", fontWeight = FontWeight.Medium, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            filePickerLauncher.launch(arrayOf("application/pdf", "application/msword",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "image/*", "text/plain"))
                        }) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导入文件", fontSize = 13.sp)
                        }
                        TextButton(onClick = { showCreateBank = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("新建题库", fontSize = 13.sp)
                        }
                    }
                }
            }
            items(vm.questionBanks) { bank ->
                QuestionBankCard(bank, vm) { selectedBankId = bank.id; showBankDetail = true }
            }
        }
    }

    if (showBankDetail) {
        QuestionBankDetailSheet(vm = vm, bankId = selectedBankId, onDismiss = { showBankDetail = false })
    }
    if (showCreateBank) {
        CreateBankDialog(vm = vm, onDismiss = { showCreateBank = false })
    }
    if (showCalendar) {
        CheckInCalendarDialog(vm = vm, onDismiss = { showCalendar = false })
    }
    if (showMakeAudio) {
        MakeAudioDialog(vm = vm, onDismiss = { showMakeAudio = false })
    }
    if (showListenAudio) {
        ListenAudioDialog(vm = vm, onDismiss = { showListenAudio = false })
    }
    if (showWrongBookDialog) {
        WrongBookDialog(vm = vm, onDismiss = { showWrongBookDialog = false })
    }
    if (showFavoritesDialog) {
        FavoritesDialog(vm = vm, onDismiss = { showFavoritesDialog = false })
    }
    if (showStudyPlan) {
        StudyPlanDialog(vm = vm, onDismiss = { showStudyPlan = false })
    }
    if (showMindMap) {
        FullScreenDialog(onDismissRequest = { showMindMap = false }) {
            MindMapPage(onBack = { showMindMap = false })
        }
    }
    if (showExamSetup) {
        ExamSetupDialog(vm = vm, onDismiss = { showExamSetup = false })
    }
    if (showFileImportDialog && pendingFileUri != null) {
        FileImportDialog(
            vm = vm,
            uri = pendingFileUri!!,
            onDismiss = {
                showFileImportDialog = false
                pendingFileUri = null
            }
        )
    }
    clickedQuestion?.let { q ->
        QuestionDetailDialog(question = q, vm = vm, onDismiss = { clickedQuestion = null })
    }
}

@Composable
fun CountdownCard(vm: PsyMapViewModel) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Box(modifier = Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(Color(0xFFEF6C00), Color(0xFFFF9800))))
            .padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("距离考研还有", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${vm.daysUntilExam}", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                        Text(" 天", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("连续打卡", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    Text("${vm.consecutiveCheckedDays}天", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("累计 ${vm.totalCheckedDays} 天", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun QuickActions(
    onStudyPlan: () -> Unit, onCalendar: () -> Unit,
    onWrongBook: () -> Unit, onFavorites: () -> Unit,
    onMindMap: () -> Unit, onMakeAudio: () -> Unit,
    onListen: () -> Unit, onMoreKnowledge: () -> Unit
) {
    val actions = listOf(
        Triple(Icons.Default.Schedule, "学习计划", onStudyPlan),
        Triple(Icons.Default.CalendarMonth, "打卡日历", onCalendar),
        Triple(Icons.Default.ErrorOutline, "错题本", onWrongBook),
        Triple(Icons.Default.Star, "收藏本", onFavorites),
        Triple(Icons.Default.AccountTree, "思维导图", onMindMap),
        Triple(Icons.Default.RecordVoiceOver, "制作音频", onMakeAudio),
        Triple(Icons.Default.Headphones, "磨耳朵", onListen),
        Triple(Icons.Default.Quiz, "考一考", onMoreKnowledge)
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        for (row in actions.chunked(4)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for ((icon, label, onClick) in row) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 10.dp).width(72.dp)) {
                        Box(modifier = Modifier.size(46.dp).background(Color(0xFFFFE0B2), CircleShape),
                            contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = label, tint = Color(0xFFFF8A00), modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFF333333))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuestionBankCard(bank: QuestionBank, vm: PsyMapViewModel, onClick: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        .combinedClickable(
            onClick = onClick,
            onLongClick = { if (vm.isAdmin) showDeleteConfirm = true }
        ),
        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(0.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(bank.subject.emoji, fontSize = 24.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(bank.name, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.height(2.dp))
                val count = vm.getQuestionsForBank(bank.id).size
                val types = bank.subject.availableQuestionTypes().joinToString(" · ") { it.label }
                Text("题数: $count  |  $types", fontSize = 11.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除题库") },
            text = {
                val count = vm.getQuestionsForBank(bank.id).size
                Text("确定要删除「${bank.name}」题库吗？\n该题库下的 $count 道题目也将被删除。\n此操作不可撤销。")
            },
            confirmButton = {
                Button(
                    onClick = { vm.deleteBank(bank.id); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
fun SearchResultItem(question: Question, vm: PsyMapViewModel, onClick: () -> Unit) {
    val bank = vm.questionBanks.find { it.id == question.bankId }
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(question.content, fontSize = 14.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Row {
                if (bank != null) { Text("${bank.subject.emoji} ${bank.name}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)) }
                Text("错误率: ${(question.errorRate * 100).toInt()}%", fontSize = 11.sp, color = Color.Gray)
                if (question.isFrequent) { Spacer(Modifier.width(6.dp)); Text("🔥常考", fontSize = 11.sp, color = Color(0xFFEF6C00)) }
                if (question.isMemorize) { Spacer(Modifier.width(6.dp)); Text("📖多背", fontSize = 11.sp, color = Color(0xFF1976D2)) }
            }
        }
    }
}

// ==================== 创建题库弹窗 ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateBankDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    // 题型全集供用户多选
    val allTypes = listOf(
        QuestionType.SINGLE_CHOICE, QuestionType.CASE_ANALYSIS,
        QuestionType.LONG_SENTENCE, QuestionType.SHORT_ANSWER,
        QuestionType.ESSAY, QuestionType.COMPREHENSIVE
    )
    var selectedTypes by remember { mutableStateOf(setOf(QuestionType.SINGLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.ESSAY)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建题库") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("题库名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("可选题型（多选）", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allTypes.forEach { type ->
                        FilterChip(
                            selected = type in selectedTypes,
                            onClick = {
                                selectedTypes = if (type in selectedTypes) selectedTypes - type else selectedTypes + type
                            },
                            label = { Text(type.label, fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // 根据选中的题型推断科目
                val subject = when {
                    selectedTypes.any { it == QuestionType.LONG_SENTENCE || it == QuestionType.VOCAB_PHRASE || it == QuestionType.COMPOSITION } -> Subject.ENGLISH
                    selectedTypes.any { it == QuestionType.MULTI_CHOICE } -> Subject.POLITICS
                    else -> Subject.GENERAL_PSY
                }
                vm.createBank(name.ifBlank { "新题库" }, subject)
                onDismiss()
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ==================== 学习统计弹窗 ====================
@Composable
fun StatsDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val bankStats = vm.getBankStats()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("累计正确率") },
        text = {
            Column {
                Text("总题目: ${vm.questions.size}  |  错题: ${vm.getWrongQuestions().size}  |  收藏: ${vm.getFavoriteQuestions().size}",
                    fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                bankStats.forEach { (bank, correct, total) ->
                    val rate = if (total > 0) correct.toFloat() / total else 0f
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${bank.subject.emoji} ${bank.name}", fontSize = 13.sp, modifier = Modifier.widthIn(min = 80.dp), maxLines = 1)
                        LinearProgressIndicator(progress = { rate }, modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary, trackColor = Color(0xFFE0E0E0), gapSize = 0.dp, drawStopIndicator = {})
                        Text("${(rate * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray,
                            modifier = Modifier.padding(start = 8.dp).widthIn(min = 42.dp), textAlign = TextAlign.End)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ==================== 打卡日历弹窗（支持切换月份） ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInCalendarDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var displayMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var displayYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    val todayCal = Calendar.getInstance()

    FullScreenDialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                if (displayMonth == 0) { displayMonth = 11; displayYear-- } else displayMonth--
                            }) { Icon(Icons.Default.ChevronLeft, contentDescription = "上月") }
                            Text("${displayYear}年${displayMonth + 1}月", fontWeight = FontWeight.Bold)
                            IconButton(onClick = {
                                if (displayMonth == 11) { displayMonth = 0; displayYear++ } else displayMonth++
                            }) { Icon(Icons.Default.ChevronRight, contentDescription = "下月") }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                val cal = Calendar.getInstance().apply { set(displayYear, displayMonth, 1) }
                val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
                val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val isCurrentMonth = displayYear == todayCal.get(Calendar.YEAR) && displayMonth == todayCal.get(Calendar.MONTH)
                val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf("日","一","二","三","四","五","六").forEach {
                        Text(it, fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.width(32.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                var day = 1
                for (week in 0..5) {
                    if (day > daysInMonth) break
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        for (dow in 0..6) {
                            if ((week == 0 && dow < firstDayOfWeek) || day > daysInMonth) {
                                Box(Modifier.size(32.dp))
                            } else {
                                val dateStr = String.format("%04d-%02d-%02d", displayYear, displayMonth + 1, day)
                                val dayRecord = vm.checkInRecords.find { it.date == dateStr }
                                val isChecked = dayRecord != null && vm.isDayCheckedIn(dayRecord)
                                val hasActivity = dayRecord != null && !isChecked && dayRecord.completedCount > 0
                                val isPast = if (isCurrentMonth) day < todayDay else displayYear < todayCal.get(Calendar.YEAR) || (displayYear == todayCal.get(Calendar.YEAR) && displayMonth < todayCal.get(Calendar.MONTH))
                                val isToday = isCurrentMonth && day == todayDay
                                val bgColor = when {
                                    isChecked -> Color(0xFF4CAF50)
                                    hasActivity -> Color(0xFFFF9800)
                                    isPast -> Color(0xFFEEEEEE)
                                    else -> Color.Transparent
                                }
                                Box(modifier = Modifier.size(32.dp)
                                    .background(bgColor, CircleShape)
                                    .then(if (isToday) Modifier.border(2.dp, Color(0xFFEF6C00), CircleShape) else Modifier),
                                    contentAlignment = Alignment.Center) {
                                    Text("$day", fontSize = 12.sp,
                                        color = if (isChecked || hasActivity) Color.White else Color.Black,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                                }
                                day++
                            }
                        }
                    }
                }

                // 本月统计
                Spacer(Modifier.height(8.dp))
                val monthPrefix = String.format("%04d-%02d", displayYear, displayMonth + 1)
                val monthRecords = vm.checkInRecords.filter { it.date.startsWith(monthPrefix) }
                val checkedCount = monthRecords.count { vm.isDayCheckedIn(it) }
                Text("本月打卡 $checkedCount 天", fontSize = 13.sp, color = Color.Gray)

                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(Color(0xFF4CAF50), CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text("已完成", fontSize = 11.sp, color = Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(Color(0xFFFF9800), CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text("部分完成", fontSize = 11.sp, color = Color.Gray)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(Color(0xFFEEEEEE), CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text("未打卡", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                // ==================== 整体计划 ====================
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Spacer(Modifier.height(12.dp))

                val context = LocalContext.current
                val planPrefs = remember { context.getSharedPreferences("psymap_plan", android.content.Context.MODE_PRIVATE) }
                var planImagePath by remember { mutableStateOf(planPrefs.getString("plan_image", "") ?: "") }
                var planText by remember { mutableStateOf(planPrefs.getString("plan_text", "") ?: "") }

                val planFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri == null) return@rememberLauncherForActivityResult
                    val mimeType = context.contentResolver.getType(uri) ?: ""
                    when {
                        mimeType.startsWith("image/") -> {
                            // 图片：保存到本地并显示
                            try {
                                val planDir = java.io.File(context.getExternalFilesDir(null), "plan").apply { mkdirs() }
                                val destFile = java.io.File(planDir, "plan_${System.currentTimeMillis()}.jpg")
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    destFile.outputStream().use { output -> input.copyTo(output) }
                                }
                                planImagePath = destFile.absolutePath
                                planPrefs.edit().putString("plan_image", planImagePath).apply()
                                Toast.makeText(context, "计划图片已导入", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        else -> {
                            // 文档（PDF/Word/Excel）：读取文本内容
                            try {
                                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                                if (text.isNotBlank()) {
                                    planText = text.take(5000)
                                    planPrefs.edit().putString("plan_text", planText).apply()
                                    Toast.makeText(context, "计划已导入", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "文件内容为空或格式不支持", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📋 整体计划", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row {
                        if (planImagePath.isNotBlank() || planText.isNotBlank()) {
                            TextButton(onClick = {
                                planImagePath = ""; planText = ""
                                planPrefs.edit().remove("plan_image").remove("plan_text").apply()
                            }) { Text("清除", fontSize = 12.sp, color = Color(0xFFD32F2F)) }
                        }
                        TextButton(onClick = {
                            planFilePicker.launch(arrayOf("image/*", "application/pdf", "text/*",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                "application/vnd.ms-excel", "application/msword", "*/*"))
                        }) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("导入计划", fontSize = 13.sp)
                        }
                    }
                }

                // 显示计划内容
                if (planImagePath.isNotBlank()) {
                    val planBmp = remember(planImagePath) { android.graphics.BitmapFactory.decodeFile(planImagePath) }
                    if (planBmp != null) {
                        Spacer(Modifier.height(8.dp))
                        Image(
                            bitmap = planBmp.asImageBitmap(),
                            contentDescription = "整体计划",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                        )
                    }
                }
                if (planText.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
                    ) {
                        // 尝试结构化显示（表格格式）
                        val lines = planText.lines().filter { it.isNotBlank() }
                        Column(modifier = Modifier.padding(12.dp)) {
                            lines.take(50).forEach { line ->
                                val cells = line.split(Regex("[\\t,，|｜]")).map { it.trim() }.filter { it.isNotBlank() }
                                if (cells.size > 1) {
                                    // 表格行
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        cells.forEach { cell ->
                                            Text(cell, fontSize = 12.sp, modifier = Modifier.weight(1f),
                                                color = Color(0xFF333333))
                                        }
                                    }
                                    HorizontalDivider(color = Color(0xFFEEEEEE))
                                } else {
                                    Text(line, fontSize = 13.sp, color = Color(0xFF333333),
                                        modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
                if (planImagePath.isBlank() && planText.isBlank()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("点击「导入计划」添加你的备考计划\n支持图片、PDF、Word、Excel", fontSize = 13.sp,
                            color = Color(0xFFBDBDBD), textAlign = TextAlign.Center)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

// ==================== 全部错题（全屏页面） ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WrongBookDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val wrongQuestions = vm.getWrongQuestions()
    val grouped = wrongQuestions.groupBy { it.bankId }
    var showStudySession by remember { mutableStateOf(false) }

    if (showStudySession) {
        FullScreenDialog(onDismissRequest = { showStudySession = false }) {
            StudySessionPage(vm = vm, onFinish = { showStudySession = false })
        }
        return
    }

    FullScreenDialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("错题本", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = Color(0xFFF5F5F5)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 顶部统计
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFFEF6C00), Color(0xFFFF9800))),
                            RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("错题总数", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${wrongQuestions.size}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text("涉及 ${grouped.size} 个题库", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (wrongQuestions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎉", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("暂无错题，继续保持", color = Color(0xFF999999), fontSize = 15.sp)
                        }
                    }
                } else {
                    grouped.forEach { (bankId, questions) ->
                        val bank = vm.questionBanks.find { it.id == bankId }
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable {
                                    vm.startStudySessionWithQuestions(questions.map { it.id })
                                    showStudySession = true
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(bank?.subject?.emoji ?: "📋", fontSize = 22.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(bank?.name ?: "未知题库", fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color(0xFF333333))
                                    Spacer(Modifier.weight(1f))
                                    Text("${questions.size} 题", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                questions.take(2).forEach { q ->
                                    Text("· ${q.content.take(35)}", fontSize = 12.sp, color = Color(0xFF999999),
                                        maxLines = 1, modifier = Modifier.padding(top = 2.dp, start = 32.dp))
                                }
                                if (questions.size > 2) Text("  还有 ${questions.size - 2} 题...", fontSize = 11.sp, color = Color(0xFFBDBDBD), modifier = Modifier.padding(start = 32.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ==================== 收藏题目（全屏页面） ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val favorites = vm.getFavoriteQuestions()
    val grouped = favorites.groupBy { it.bankId }
    var showStudySession by remember { mutableStateOf(false) }

    if (showStudySession) {
        FullScreenDialog(onDismissRequest = { showStudySession = false }) {
            StudySessionPage(vm = vm, onFinish = { showStudySession = false })
        }
        return
    }

    FullScreenDialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("收藏本", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = Color(0xFFF5F5F5)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 顶部统计
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFFFF9800), Color(0xFFFFC107))),
                            RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        )
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("收藏总数", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("${favorites.size}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text("涉及 ${grouped.size} 个题库", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (favorites.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⭐", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("暂无收藏", color = Color(0xFF999999), fontSize = 15.sp)
                        }
                    }
                } else {
                    grouped.forEach { (bankId, questions) ->
                        val bank = vm.questionBanks.find { it.id == bankId }
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                                .clickable {
                                    vm.startStudySessionWithQuestions(questions.map { it.id })
                                    showStudySession = true
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(bank?.subject?.emoji ?: "📋", fontSize = 22.sp)
                                    Spacer(Modifier.width(10.dp))
                                    Text(bank?.name ?: "未知题库", fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color(0xFF333333))
                                    Spacer(Modifier.weight(1f))
                                    Text("${questions.size} 题", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.height(8.dp))
                                questions.take(2).forEach { q ->
                                    Text("· ${q.content.take(35)}", fontSize = 12.sp, color = Color(0xFF999999),
                                        maxLines = 1, modifier = Modifier.padding(top = 2.dp, start = 32.dp))
                                }
                                if (questions.size > 2) Text("  还有 ${questions.size - 2} 题...", fontSize = 11.sp, color = Color(0xFFBDBDBD), modifier = Modifier.padding(start = 32.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ==================== 文件导入（全屏页面） ====================
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FileImportDialog(vm: PsyMapViewModel, uri: Uri, onDismiss: () -> Unit) {
    var selectedBankId by remember { mutableStateOf(vm.questionBanks.firstOrNull()?.id ?: "") }
    var newBankName by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }
    var newBankSubject by remember { mutableStateOf(Subject.GENERAL_PSY) }
    val selectedBank = vm.questionBanks.find { it.id == selectedBankId }
    val availableTypes = selectedBank?.subject?.availableQuestionTypes() ?: QuestionType.entries.toList()
    var selectedType by remember { mutableStateOf<QuestionType?>(null) }
    var tagFrequent by remember { mutableStateOf(false) }
    var tagMemorize by remember { mutableStateOf(true) }  // 导入默认多背
    val context = LocalContext.current
    val isLoading by vm.isLoading.collectAsState()

    // 打开时清除旧的导入结果消息
    LaunchedEffect(Unit) { vm.importResultMessage = "" }

    // 监听导入结果
    LaunchedEffect(vm.importResultMessage) {
        if (vm.importResultMessage.isNotBlank()) {
            Toast.makeText(context, vm.importResultMessage, Toast.LENGTH_LONG).show()
            if (vm.importResultMessage.startsWith("成功")) onDismiss()
        }
    }

    // 保存按钮需要的状态
    var importBankId by remember { mutableStateOf("") }

    fun doImport() {
        val bankId = if (isCreatingNew && newBankName.isNotBlank()) {
            vm.createBank(newBankName, newBankSubject).id
        } else selectedBankId

        if (bankId.isBlank()) {
            Toast.makeText(context, "请选择或新建题库", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val mimeType = context.contentResolver.getType(uri) ?: ""
            val isPdf = mimeType.contains("pdf", ignoreCase = true)
            val isImage = mimeType.startsWith("image/", ignoreCase = true)

            if (isImage) {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bmp = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bmp != null) {
                    vm.recognizeAndImport(bmp, bankId, selectedType, tagFrequent, tagMemorize)
                } else {
                    Toast.makeText(context, "图片加载失败", Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            } else if (isPdf) {
                val fd = context.contentResolver.openFileDescriptor(uri, "r")
                if (fd == null) {
                    Toast.makeText(context, "无法打开PDF", Toast.LENGTH_SHORT).show()
                    return
                }
                val renderer = android.graphics.pdf.PdfRenderer(fd)
                val maxPages = minOf(renderer.pageCount, 5)
                for (i in 0 until maxPages) {
                    val page = renderer.openPage(i)
                    val bmp = android.graphics.Bitmap.createBitmap(page.width * 2, page.height * 2, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    vm.recognizeAndImport(bmp, bankId, selectedType, tagFrequent, tagMemorize)
                }
                renderer.close()
                fd.close()
                onDismiss()
            } else {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Toast.makeText(context, "无法读取文件", Toast.LENGTH_SHORT).show()
                    return
                }
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                val sb = StringBuilder()
                val maxChars = 15000
                val buffer = CharArray(4096)
                var totalRead = 0
                var read: Int
                while (reader.read(buffer).also { read = it } != -1 && totalRead < maxChars) {
                    val toAppend = minOf(read, maxChars - totalRead)
                    sb.append(buffer, 0, toAppend)
                    totalRead += toAppend
                }
                reader.close()
                val text = sb.toString()
                if (text.isBlank()) {
                    Toast.makeText(context, "文件内容为空或格式不支持", Toast.LENGTH_SHORT).show()
                    return
                }
                vm.importFromFileContent(text, bankId, selectedType, tagFrequent, tagMemorize)
                onDismiss()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "文件读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    FullScreenDialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("导入文件到题库") },
                    navigationIcon = {
                        IconButton(onClick = { if (!isLoading) onDismiss() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
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
                // 选择已有题库 or 新建
                Text("选择题库", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))

                vm.questionBanks.forEach { bank ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedBankId = bank.id; isCreatingNew = false
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedBankId == bank.id && !isCreatingNew,
                            onClick = { selectedBankId = bank.id; isCreatingNew = false })
                        Spacer(Modifier.width(4.dp))
                        Text("${bank.subject.emoji} ${bank.name}", fontSize = 14.sp)
                    }
                }

                // 新建题库选项
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { isCreatingNew = true }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isCreatingNew, onClick = { isCreatingNew = true })
                    Spacer(Modifier.width(4.dp))
                    Text("+ 新建题库", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                }

                if (isCreatingNew) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newBankName, onValueChange = { newBankName = it },
                        label = { Text("题库名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("科目", fontSize = 12.sp, color = Color.Gray)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Subject.entries.forEach { s ->
                            FilterChip(selected = newBankSubject == s, onClick = { newBankSubject = s },
                                label = { Text("${s.emoji}${s.label}", fontSize = 11.sp) })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // 题目分类选择（根据题库科目动态变化）
                val typesForBank = if (isCreatingNew) newBankSubject.availableQuestionTypes()
                    else selectedBank?.subject?.availableQuestionTypes() ?: emptyList()

                Text("题目分类（可选，不选则由AI自动判断）", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    typesForBank.forEach { type ->
                        FilterChip(selected = selectedType == type,
                            onClick = { selectedType = if (selectedType == type) null else type },
                            label = { Text(type.label, fontSize = 11.sp) })
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("标签", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(selected = tagFrequent, onClick = { tagFrequent = !tagFrequent },
                        label = { Text("🔥 常考", fontSize = 11.sp) })
                    FilterChip(selected = tagMemorize, onClick = { tagMemorize = !tagMemorize },
                        label = { Text("📖 多背", fontSize = 11.sp) })
                }

                if (isLoading) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 正在解析文档...", fontSize = 13.sp, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { doImport() },
                    enabled = !isLoading && (selectedBankId.isNotBlank() || (isCreatingNew && newBankName.isNotBlank())),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00))
                ) { Text("开始导入", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ==================== 学习计划（全屏页面） ====================
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StudyPlanDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var showEdit by remember { mutableStateOf(false) }
    var showAiPlan by remember { mutableStateOf(false) }

    // 统计
    val totalTarget = vm.questionBanks.sumOf { vm.dailyTargets[it.id] ?: 0 }
    val totalDone = vm.questionBanks.sumOf { vm.todayCheckIn.bankProgress[it.id] ?: 0 }
    val overallProgress = if (totalTarget > 0) (totalDone.toFloat() / totalTarget).coerceAtMost(1f) else 0f

    FullScreenDialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("学习计划", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showEdit = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑", tint = Color(0xFF333333))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = Color(0xFFF5F5F5)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 顶部总览卡片
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFFFF8A00), Color(0xFFFF6D00))),
                            RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("今日进度", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "$totalDone / $totalTarget",
                            color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { overallProgress },
                            modifier = Modifier.fillMaxWidth(0.7f).height(6.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {}
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (totalTarget == 0) "暂未设定计划" else "${(overallProgress * 100).toInt()}% 已完成",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 各题库进度卡片
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("题库进度", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                        Spacer(Modifier.height(12.dp))

                        vm.questionBanks.forEachIndexed { index, bank ->
                            val target = vm.dailyTargets[bank.id] ?: 0
                            val done = vm.todayCheckIn.bankProgress[bank.id] ?: 0
                            val progress = if (target > 0) (done.toFloat() / target).coerceAtMost(1f) else 0f
                            val isComplete = target > 0 && done >= target

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 左侧 emoji + 名称
                                Text(bank.subject.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bank.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
                                    if (target > 0) {
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { progress },
                                            modifier = Modifier.fillMaxWidth().height(4.dp),
                                            color = if (isComplete) Color(0xFF4CAF50) else Color(0xFFFF8A00),
                                            trackColor = Color(0xFFF0F0F0),
                                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                                            gapSize = 0.dp,
                                            drawStopIndicator = {}
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                // 右侧数字
                                if (target > 0) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "$done/$target",
                                            fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                            color = if (isComplete) Color(0xFF4CAF50) else Color(0xFFFF8A00)
                                        )
                                        if (isComplete) {
                                            Text("✓ 已完成", fontSize = 10.sp, color = Color(0xFF4CAF50))
                                        }
                                    }
                                } else {
                                    Text("未设定", fontSize = 13.sp, color = Color(0xFFBDBDBD))
                                }
                            }
                            if (index < vm.questionBanks.size - 1) {
                                HorizontalDivider(color = Color(0xFFF5F5F5))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // AI 制定计划按钮
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable { showAiPlan = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFFFF3E0), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null,
                                tint = Color(0xFFFF8A00), modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI 智能制定计划", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333))
                            Text("描述你的学习需求，AI帮你规划", fontSize = 12.sp, color = Color(0xFF999999))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color(0xFFCCCCCC))
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showEdit) {
        EditDailyTargetsDialog(vm = vm, onDismiss = { showEdit = false })
    }
    if (showAiPlan) {
        AiPlanDialog(vm = vm, onDismiss = { showAiPlan = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditDailyTargetsDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val targets = remember { mutableStateMapOf<String, String>().apply {
        vm.questionBanks.forEach { bank -> put(bank.id, (vm.dailyTargets[bank.id] ?: 10).toString()) }
    }}
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑每日学习计划") },
        text = {
            Column {
                Text("设定每天的学习计划，并创建提醒", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                vm.questionBanks.forEach { bank ->
                    val bankQuestionCount = vm.getQuestionsForBank(bank.id).size
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${bank.subject.emoji} ${bank.name}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        NumberStepper(
                            value = targets[bank.id] ?: "10",
                            onValueChange = { newVal ->
                                val num = newVal.toIntOrNull() ?: 0
                                targets[bank.id] = num.coerceAtMost(bankQuestionCount).toString()
                            },
                            min = 0, max = bankQuestionCount, suffix = ""
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val map = targets.mapValues { entry ->
                    val bankCount = vm.getQuestionsForBank(entry.key).size
                    (entry.value.toIntOrNull() ?: 10).coerceIn(0, bankCount)
                }
                vm.saveDailyTargets(map)
                val activeBanks = vm.questionBanks.filter { (map[it.id] ?: 0) > 0 }
                if (activeBanks.isNotEmpty()) {
                    try {
                        val desc = activeBanks.joinToString("\n") { bank ->
                            "${bank.subject.emoji} ${bank.name}: ${map[bank.id] ?: 10}题"
                        }
                        val cal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 16)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            if (before(java.util.Calendar.getInstance())) add(java.util.Calendar.DAY_OF_YEAR, 1)
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                            data = android.provider.CalendarContract.Events.CONTENT_URI
                            putExtra(android.provider.CalendarContract.Events.TITLE, "PsyMap考研复习 (${activeBanks.size}个题库)")
                            putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "每日学习任务:\n$desc")
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                            putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 30 * 60 * 1000)
                            putExtra(android.provider.CalendarContract.Events.RRULE, "FREQ=DAILY;COUNT=90")
                            putExtra(android.provider.CalendarContract.Events.HAS_ALARM, true)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
                onDismiss()
            }) { Text("保存并创建提醒") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ==================== 制作音频（全屏页面） ====================
// TTS 音色选项
data class VoiceOption(val id: String, val label: String, val tencentId: Int, val edgeVoice: String)
private val voiceOptions = listOf(
    VoiceOption("female1", "温柔女声", 101001, "zh-CN-XiaoxiaoNeural"),      // 腾讯:智瑜情感女声 / Edge:晓晓
    VoiceOption("female2", "知性女声", 101011, "zh-CN-XiaohanNeural"),       // 腾讯:智燕新闻女声 / Edge:晓涵
    VoiceOption("male2", "沉稳男声", 101013, "zh-CN-YunjianNeural")         // 腾讯:智辉新闻男声 / Edge:云健
)

// 参与音频制作的题型（排除单选题、多选题）
private val ttsQuestionTypes = setOf(
    QuestionType.CASE_ANALYSIS, QuestionType.SHORT_ANSWER, QuestionType.ESSAY,
    QuestionType.COMPREHENSIVE, QuestionType.VOCAB_PHRASE, QuestionType.LONG_SENTENCE,
    QuestionType.COMPOSITION
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MakeAudioDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var selectedBankId by remember { mutableStateOf(vm.questionBanks.firstOrNull()?.id ?: "") }
    var questionCount by remember { mutableStateOf("10") }
    var shuffle by remember { mutableStateOf(false) }
    var selectedVoice by remember { mutableStateOf(voiceOptions[0]) }
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    FullScreenDialog(onDismissRequest = { if (!isGenerating) onDismiss() }) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("制作音频", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { if (!isGenerating) onDismiss() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            containerColor = Color(0xFFF5F5F5)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 选择题库
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("选择题库", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                        Spacer(Modifier.height(4.dp))
                        vm.questionBanks.forEach { bank ->
                            val count = vm.getQuestionsForBank(bank.id).size
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable { selectedBankId = bank.id }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedBankId == bank.id,
                                    onClick = { selectedBankId = bank.id },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF8A00)),
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("${bank.subject.emoji} ${bank.name}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("${count}题", fontSize = 12.sp, color = Color(0xFFBDBDBD))
                            }
                        }
                    }
                }

                // 音频设置卡片
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        // 题目数量 + 乱序 同一行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("题目数量", fontSize = 14.sp, color = Color(0xFF666666))
                            Spacer(Modifier.width(12.dp))
                            NumberStepper(value = questionCount, onValueChange = { questionCount = it }, min = 1, max = 100)
                            Spacer(Modifier.weight(1f))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("乱序", fontSize = 14.sp, color = Color(0xFF666666))
                                Spacer(Modifier.width(4.dp))
                                Switch(
                                    checked = shuffle,
                                    onCheckedChange = { shuffle = it },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFFFF8A00))
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFF5F5F5))
                        Spacer(Modifier.height(12.dp))

                        // 音色选择
                        Text("音色选择", fontSize = 14.sp, color = Color(0xFF666666))
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            voiceOptions.forEach { voice ->
                                FilterChip(
                                    selected = selectedVoice.id == voice.id,
                                    onClick = { selectedVoice = voice },
                                    label = { Text(voice.label, fontSize = 13.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFFF3E0),
                                        selectedLabelColor = Color(0xFFFF8A00)
                                    )
                                )
                            }
                        }

                        // 显示可用题目数（检查磨耳朵目录中是否已有同名音频）
                        Spacer(Modifier.height(8.dp))
                        val totalSubjective = vm.getQuestionsForBank(selectedBankId).count { it.type in ttsQuestionTypes }
                        Text("主观题: ${totalSubjective}题",
                            fontSize = 11.sp, color = Color(0xFF999999))
                    }
                }

                // 生成进度
                if (isGenerating) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color(0xFFFF8A00))
                            Spacer(Modifier.width(12.dp))
                            Text(progress, fontSize = 13.sp, color = Color(0xFF666666))
                        }
                    }
                }

                // 开始制作按钮
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                    isGenerating = true
                    progress = "准备中..."
                    val bankQuestions = vm.getQuestionsForBank(selectedBankId)
                    val bank = vm.questionBanks.find { it.id == selectedBankId }
                    val bankName = bank?.name ?: "题库"
                    val voiceOpt = selectedVoice
                    val cnt = (questionCount.toIntOrNull() ?: 10)

                    // 只选主观题（不再检查 ttsGenerated 标记）
                    val pendingQuestions = bankQuestions.filter { it.type in ttsQuestionTypes }
                    val selected = if (shuffle) {
                        pendingQuestions.shuffled().take(cnt)
                    } else {
                        pendingQuestions.take(cnt)
                    }

                    if (selected.isEmpty()) {
                        isGenerating = false; progress = ""
                        Toast.makeText(context, "所有题目都已生成过音频", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // 计算题号（在完整题库中的位置）
                    val startIdx = bankQuestions.indexOf(selected.first()) + 1
                    val endIdx = bankQuestions.indexOf(selected.last()) + 1
                    val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.CHINA).format(java.util.Date())

                    val fullText = StringBuilder()
                    selected.forEachIndexed { idx, q ->
                        val qIdx = bankQuestions.indexOf(q) + 1
                        fullText.append("第${qIdx}题。${q.content}。答案：${q.answer}。")
                    }

                    val audioDir = java.io.File(context.getExternalFilesDir(null), "audio")
                    audioDir.mkdirs()

                    scope.launch(Dispatchers.IO) {
                        try {
                            withContext(Dispatchers.Main) { progress = "正在生成音频..." }
                            val audioBytes = tencentTtsWithFallback(fullText.toString(), voiceOpt)

                            if (audioBytes != null && audioBytes.isNotEmpty()) {
                                val durationSec = audioBytes.size / 2000
                                val durationStr = if (durationSec >= 60) "${durationSec / 60}m${durationSec % 60}s" else "${durationSec}s"
                                val fileName = "${bankName}-${startIdx}到${endIdx}题-${durationStr}-${dateStr}.mp3"
                                val audioFile = java.io.File(audioDir, fileName)
                                audioFile.writeBytes(audioBytes)

                                // 保存元数据（题目ID列表 + 每题字符数用于进度条标记）
                                val metaFile = java.io.File(audioDir, "$fileName.meta")
                                val questionIds = selected.map { it.id }
                                val charCounts = selected.map { q -> "第${bankQuestions.indexOf(q)+1}题。${q.content}。答案：${q.answer}。".length }
                                val meta = mapOf("questionIds" to questionIds, "charCounts" to charCounts)
                                metaFile.writeText(com.google.gson.Gson().toJson(meta))

                                withContext(Dispatchers.Main) {
                                    isGenerating = false; progress = ""
                                    Toast.makeText(context, "音频已保存: $fileName\n可在「磨耳朵」中播放", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    isGenerating = false; progress = ""
                                    Toast.makeText(context, "音频生成失败，请检查网络连接", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PsyMap-TTS", "TTS exception", e)
                            withContext(Dispatchers.Main) {
                                isGenerating = false; progress = ""
                                Toast.makeText(context, "生成失败: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                    enabled = !isGenerating && selectedBankId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00))
                ) {
                    Text(if (isGenerating) "生成中..." else "开始制作", fontSize = 15.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ==================== 磨耳朵（全屏页面） ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenAudioDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audioDir = remember { java.io.File(context.getExternalFilesDir(null), "audio").apply { mkdirs() } }
    val prefs = remember { context.getSharedPreferences("psymap_audio", android.content.Context.MODE_PRIVATE) }
    // 被移除（隐藏）的文件名集合
    var hiddenFiles by remember {
        mutableStateOf(prefs.getStringSet("hidden_audio", emptySet())?.toSet() ?: emptySet())
    }
    var allFiles by remember { mutableStateOf(audioDir.listFiles()?.filter { it.extension in listOf("wav", "mp3", "ogg") }?.sortedBy { it.name }?.toList() ?: emptyList()) }
    // 显示的文件 = 磁盘上存在 且 未被隐藏
    val audioFiles = allFiles.filter { it.name !in hiddenFiles }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaying by remember { mutableStateOf("") }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    fun refreshFiles() {
        allFiles = audioDir.listFiles()?.filter { it.extension in listOf("wav", "mp3", "ogg") }?.sortedBy { it.name }?.toList() ?: emptyList()
    }

    // 本地文件选择器
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try {
                val name = uri.lastPathSegment?.substringAfterLast("/") ?: "audio_${System.currentTimeMillis()}.mp3"
                val dest = java.io.File(audioDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (_: Exception) {}
        }
        refreshFiles()
    }

    var playProgress by remember { mutableStateOf(0f) }
    var playDuration by remember { mutableStateOf(0) }
    var playPosition by remember { mutableStateOf(0) }
    var playlistIndex by remember { mutableStateOf(0) }
    var playlist by remember { mutableStateOf(listOf<java.io.File>()) }

    // 更新播放进度
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                val mp = mediaPlayer
                if (mp != null && mp.isPlaying) {
                    playPosition = mp.currentPosition
                    playDuration = mp.duration
                    playProgress = if (playDuration > 0) playPosition.toFloat() / playDuration else 0f
                }
            } catch (_: Exception) {}
            kotlinx.coroutines.delay(500)
        }
    }

    var chapterMarkers by remember { mutableStateOf(listOf<Float>()) }  // 每题开始位置（0-1）

    fun playNext(index: Int) {
        if (index >= playlist.size) { isPlaying = false; currentPlaying = ""; playProgress = 0f; chapterMarkers = emptyList(); return }
        playlistIndex = index
        currentPlaying = playlist[index].nameWithoutExtension
        // 加载章节标记
        val metaFile = java.io.File(audioDir, "${playlist[index].name}.meta")
        chapterMarkers = try {
            if (metaFile.exists()) {
                val meta = com.google.gson.Gson().fromJson<Map<String, Any>>(
                    metaFile.readText(), object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val counts = (meta["charCounts"] as? List<Double>)?.map { it.toInt() } ?: emptyList()
                if (counts.size > 1) {
                    val total = counts.sum().toFloat()
                    val markers = mutableListOf<Float>()
                    var cumulative = 0
                    for (i in 0 until counts.size - 1) {
                        cumulative += counts[i]
                        markers.add(cumulative / total)
                    }
                    android.util.Log.d("PsyMap-TTS", "Chapter markers: $markers (${counts.size} questions)")
                    markers
                } else emptyList()
            } else emptyList()
        } catch (_: Exception) { emptyList() }

        mediaPlayer = null  // 使用 Service 播放
        // 使用前台 Service 播放（不会被系统回收）
        val svcIntent = android.content.Intent(context, AudioPlaybackService::class.java).apply {
            action = "PLAY"
            putStringArrayListExtra("paths", ArrayList(playlist.map { it.absolutePath }))
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(svcIntent)
        } else {
            context.startService(svcIntent)
        }
        // 等 Service 启动后设置 index
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            AudioPlaybackService.instance?.let { svc ->
                svc.playlist = playlist.map { it.absolutePath }
                if (index > 0) svc.playFile(index)
            }
        }, 500)
        isPlaying = true
    }

    // 从 Service 同步播放状态
    LaunchedEffect(isPlaying) {
        while (true) {
            val svc = AudioPlaybackService.instance
            if (svc != null) {
                isPlaying = svc.isPlaying
                currentPlaying = svc.currentFileName
                playlistIndex = svc.currentIndex
                playDuration = svc.duration
                playPosition = svc.currentPosition
                playProgress = if (playDuration > 0) playPosition.toFloat() / playDuration else 0f
            }
            kotlinx.coroutines.delay(500)
        }
    }

    FullScreenDialog(
        onDismissRequest = { onDismiss() }  // 不停止播放，允许后台继续
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("磨耳朵", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { onDismiss() }) {  // 返回不停止播放
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                            Text("+ 导入音频", fontSize = 13.sp)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                if (audioFiles.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 8.dp,
                        color = Color.White
                    ) {
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp)) {
                            if (isPlaying || currentPlaying.isNotBlank()) {
                                // 播放进度条
                                Text(currentPlaying, fontSize = 12.sp, color = Color(0xFFFF8A00), maxLines = 1,
                                    modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(4.dp))
                                // 进度条 + 题目分隔标记
                                Box(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                                    Slider(
                                        value = playProgress,
                                        onValueChange = { newVal ->
                                            playProgress = newVal
                                            AudioPlaybackService.instance?.seekTo((newVal * playDuration).toInt())
                                        },
                                        modifier = Modifier.fillMaxWidth().height(24.dp),
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF8A00), activeTrackColor = Color(0xFFFF8A00))
                                    )
                                    // 题目分隔竖线标记
                                    if (chapterMarkers.isNotEmpty()) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                                            val trackWidth = size.width - 40f
                                            val offsetX = 20f
                                            chapterMarkers.forEach { pos ->
                                                val x = offsetX + pos * trackWidth
                                                drawLine(
                                                    color = androidx.compose.ui.graphics.Color(0xFFD32F2F),
                                                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                                                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                                                    strokeWidth = 3f
                                                )
                                            }
                                        }
                                    } else {
                                        // 旧音频无标记数据提示
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    fun fmtTime(ms: Int): String { val s = ms / 1000; return "${s / 60}:%02d".format(s % 60) }
                                    Text(fmtTime(playPosition), fontSize = 11.sp, color = Color.Gray)
                                    Text("${playlistIndex + 1}/${playlist.size}", fontSize = 11.sp, color = Color.Gray)
                                    Text(fmtTime(playDuration), fontSize = 11.sp, color = Color.Gray)
                                }
                                Spacer(Modifier.height(4.dp))
                                // 播放控制按钮
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = {
                                        val svc = AudioPlaybackService.instance
                                        if (svc != null && playlistIndex > 0) {
                                            svc.playFile(playlistIndex - 1)
                                            // 重新加载章节标记
                                            if (playlistIndex - 1 < playlist.size) {
                                                val mf = java.io.File(audioDir, "${playlist[playlistIndex - 1].name}.meta")
                                                // markers will update via LaunchedEffect
                                            }
                                        }
                                    }, enabled = playlistIndex > 0) {
                                        Icon(Icons.Default.SkipPrevious, contentDescription = "上一首", modifier = Modifier.size(32.dp))
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    IconButton(onClick = {
                                        val svc = AudioPlaybackService.instance
                                        if (svc != null) {
                                            if (svc.isPlaying) {
                                                svc.mediaPlayer?.pause(); svc.isPlaying = false
                                            } else {
                                                svc.mediaPlayer?.start(); svc.isPlaying = true
                                            }
                                            isPlaying = svc.isPlaying
                                        }
                                    }) {
                                        Icon(if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                            contentDescription = if (isPlaying) "暂停" else "播放",
                                            modifier = Modifier.size(48.dp), tint = Color(0xFFFF8A00))
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    IconButton(onClick = {
                                        val svc = AudioPlaybackService.instance
                                        if (svc != null && playlistIndex < playlist.size - 1) svc.playFile(playlistIndex + 1)
                                    }, enabled = playlistIndex < playlist.size - 1) {
                                        Icon(Icons.Default.SkipNext, contentDescription = "下一首", modifier = Modifier.size(32.dp))
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    IconButton(onClick = {
                                        val stopIntent = android.content.Intent(context, AudioPlaybackService::class.java).apply { action = "STOP" }
                                        context.startService(stopIntent)
                                        isPlaying = false; currentPlaying = ""; playProgress = 0f; chapterMarkers = emptyList()
                                    }) {
                                        Icon(Icons.Default.Stop, contentDescription = "停止", modifier = Modifier.size(28.dp), tint = Color(0xFFD32F2F))
                                    }
                                }
                            } else {
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (selectedFiles.isNotEmpty()) {
                                            hiddenFiles = hiddenFiles + selectedFiles
                                            prefs.edit().putStringSet("hidden_audio", hiddenFiles).apply()
                                            selectedFiles = emptySet()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = selectedFiles.isNotEmpty() && !isPlaying,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF666666))
                                ) { Text("移除", fontSize = 13.sp) }

                                OutlinedButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = selectedFiles.isNotEmpty() && !isPlaying,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F))
                                ) { Text("删除", fontSize = 13.sp) }

                                Button(
                                    onClick = {
                                        playlist = audioFiles.filter { it.name in selectedFiles }
                                        if (playlist.isNotEmpty()) {
                                            isPlaying = true
                                            playNext(0)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = selectedFiles.isNotEmpty() && !isPlaying,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00))
                                ) { Text("▶ 播放", fontSize = 13.sp) }
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFFF5F5F5)
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (audioFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🎧", fontSize = 48.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("暂无音频文件", color = Color(0xFF999999), fontSize = 15.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("使用「制作音频」生成 或 点击「导入音频」", color = Color(0xFFBDBDBD), fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("音频目录: ${audioDir.absolutePath}", color = Color(0xFFBDBDBD), fontSize = 10.sp)
                        }
                    }
                } else {
                    // 文件列表
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("选择要播放的音频文件 (${audioFiles.size}个)", fontSize = 13.sp, color = Color(0xFF999999))
                        Spacer(Modifier.height(8.dp))
                        audioFiles.forEach { file ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (file.name in selectedFiles) Color(0xFFFFF3E0) else Color.White
                                ),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFiles = if (file.name in selectedFiles) selectedFiles - file.name else selectedFiles + file.name
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = file.name in selectedFiles,
                                        onCheckedChange = { selectedFiles = if (it) selectedFiles + file.name else selectedFiles - file.name },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFFFF8A00))
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.nameWithoutExtension, fontSize = 14.sp, color = Color(0xFF333333), maxLines = 1)
                                        Text("${file.length() / 1024} KB", fontSize = 11.sp, color = Color(0xFFBDBDBD))
                                    }
                                    if (isPlaying && currentPlaying == file.nameWithoutExtension) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFFF8A00))
                                    }
                                }
                            }
                        }
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
            text = { Text("将永久删除选中的 ${selectedFiles.size} 个音频文件，此操作不可撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        selectedFiles.forEach { name ->
                            // 删除音频文件
                            val metaFile = java.io.File(audioDir, "$name.meta")
                            if (metaFile.exists()) {
                                try {
                                    val meta = com.google.gson.Gson().fromJson<Map<String, Any>>(
                                        metaFile.readText(),
                                        object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                                    )
                                    @Suppress("UNCHECKED_CAST")
                                    val ids = (meta["questionIds"] as? List<String>) ?: emptyList()
                                    if (ids.isNotEmpty()) {
                                        // 元数据已清理
                                    }
                                    metaFile.delete()
                                } catch (_: Exception) {}
                            }
                            java.io.File(audioDir, name).delete()
                        }
                        selectedFiles = emptySet()
                        refreshFiles()
                        showDeleteConfirm = false
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

// ==================== AI制定学习计划弹窗 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPlanDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var userInput by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var parsedTargets by remember { mutableStateOf(mapOf<String, Int>()) }
    val context = LocalContext.current

    FullScreenDialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AI制定计划")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { if (!isLoading) onDismiss() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (!showPreview) {
                    Text("描述你的学习需求，AI帮你制定每日计划", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                    Text("示例：\n• 政治：1000题，4-5月完成，每天30分钟\n• 英语：记单词，每天30词，30分钟\n• 普心：思维导图，每天一章，1小时",
                        fontSize = 11.sp, color = Color(0xFFBDBDBD))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        label = { Text("输入你的学习需求") },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        maxLines = 8
                    )

                    Spacer(Modifier.height(12.dp))

                    if (isLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("AI正在制定计划...", fontSize = 13.sp, color = Color.Gray)
                        }
                    }

                    if (aiResponse.isNotBlank() && !showPreview) {
                        Spacer(Modifier.height(8.dp))
                        Text("AI建议", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                            Text(aiResponse, fontSize = 13.sp, modifier = Modifier.padding(12.dp), lineHeight = 20.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { if (!isLoading) onDismiss() }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (userInput.isBlank()) return@Button
                                isLoading = true
                                val bankNames = vm.questionBanks.joinToString("、") { it.name }
                                val prompt = """你是考研备考规划专家。用户有以下题库：$bankNames。
请根据用户的学习需求，为每个相关题库制定每日学习题目数量。
你必须返回两部分：
1. 一段简短的学习建议文字
2. 一个JSON对象，key是题库名称，value是每天的题目数量
格式示例：
建议：根据你的需求，建议政治每天做17题...
计划：{"政治":17,"英语":30,"普心":15}
注意：计划部分必须单独一行，以"计划："开头"""

                                if (!vm.aiEnabled) {
                                    isLoading = false
                                    aiResponse = "请在设置中启用AI功能"
                                    return@Button
                                }
                                AiService.chatCompletion(prompt, userInput, { result ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        isLoading = false
                                        aiResponse = result

                                        // 解析AI返回的计划
                                        val planLine = result.lines().find { it.trimStart().startsWith("计划") || it.contains("{") }
                                        if (planLine != null) {
                                            try {
                                                val jsonStr = planLine.substringAfter("{").substringBefore("}").let { "{$it}" }
                                                val planMap = com.google.gson.Gson().fromJson<Map<String, Double>>(jsonStr,
                                                    object : com.google.gson.reflect.TypeToken<Map<String, Double>>() {}.type)
                                                val targets = mutableMapOf<String, Int>()
                                                // 保留现有目标
                                                vm.dailyTargets.forEach { (k, v) -> targets[k] = v }
                                                // 匹配题库名称到ID
                                                planMap.forEach { (name, count) ->
                                                    val bank = vm.questionBanks.find { it.name.contains(name) || name.contains(it.name) }
                                                    if (bank != null) targets[bank.id] = count.toInt()
                                                }
                                                parsedTargets = targets
                                                showPreview = true
                                            } catch (_: Exception) {
                                                // 解析失败，显示原始文本
                                            }
                                        }
                                    }
                                }, { error ->
                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                        isLoading = false
                                        aiResponse = "AI请求失败: $error"
                                    }
                                })
                            },
                            enabled = !isLoading && userInput.isNotBlank()
                        ) { Text("生成计划") }
                    }
                } else {
                    // 预览模式
                    Text("AI制定的学习计划预览", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    vm.questionBanks.forEach { bank ->
                        val target = parsedTargets[bank.id] ?: 0
                        if (target > 0) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${bank.subject.emoji} ${bank.name}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("每天 $target 题", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                        Text(aiResponse, fontSize = 12.sp, modifier = Modifier.padding(12.dp), lineHeight = 18.sp, color = Color.Gray)
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showPreview = false }) { Text("返回修改") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {
                            vm.saveDailyTargets(parsedTargets)
                            Toast.makeText(context, "学习计划已更新！", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }) { Text("确认应用") }
                    }
                }
            }
        }
    }
}

// ==================== 考一考（模拟考试模块） ====================

/** 根据题型返回每题分钟数 */
private fun QuestionType.minutesPerQuestion(): Int = when (this) {
    QuestionType.VOCAB_PHRASE, QuestionType.LONG_SENTENCE,
    QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE -> 1
    QuestionType.SHORT_ANSWER -> 5
    QuestionType.ESSAY -> 10
    QuestionType.CASE_ANALYSIS, QuestionType.COMPOSITION, QuestionType.COMPREHENSIVE -> 15
}

/** 根据时间和比例生成考试题目 */
private fun generateExamQuestions(
    allQuestions: List<Question>,
    totalMinutes: Int,
    includeTypes: Set<QuestionType>
): List<Question> {
    // 按记忆曲线紧迫度排序（紧迫的优先入选考试）
    val available = allQuestions.filter { it.type in includeTypes }
    val scored = available.map { q ->
        val totalAttempts = q.correctCount + q.wrongCount
        val correctRate = q.correctRate
        val idealInterval = when {
            totalAttempts == 0 -> 0.0; correctRate < 0.4 -> 1.0; correctRate < 0.6 -> 2.0
            correctRate < 0.75 -> 4.0; correctRate < 0.85 -> 7.0; correctRate < 0.95 -> 15.0; else -> 30.0
        }
        val daysSince = if (q.lastStudiedAt > 0) (System.currentTimeMillis() - q.lastStudiedAt).toDouble() / 86400000.0 else 999.0
        val overdue = daysSince - idealInterval
        val urgency = (1.0 - correctRate) * 40 + (overdue * 5).coerceIn(0.0, 40.0) +
            (if (totalAttempts == 0) 15.0 else 0.0) + (if (q.isInWrongBook) 10.0 else 0.0)
        q to urgency
    }.sortedByDescending { it.second }.map { it.first }

    val fastTypes = setOf(QuestionType.VOCAB_PHRASE, QuestionType.LONG_SENTENCE, QuestionType.SINGLE_CHOICE, QuestionType.MULTI_CHOICE)
    val midTypes = setOf(QuestionType.SHORT_ANSWER)
    val slowTypes = setOf(QuestionType.ESSAY, QuestionType.CASE_ANALYSIS, QuestionType.COMPOSITION, QuestionType.COMPREHENSIVE)
    val fastMinutes = (totalMinutes * 0.5).toInt()
    val midMinutes = (totalMinutes * 0.3).toInt()
    val slowMinutes = totalMinutes - fastMinutes - midMinutes

    fun pickQuestions(pool: List<Question>, maxMinutes: Int): List<Question> {
        val result = mutableListOf<Question>()
        var usedMinutes = 0
        for (q in pool) {
            val cost = q.type.minutesPerQuestion()
            if (usedMinutes + cost <= maxMinutes) { result.add(q); usedMinutes += cost }
        }
        return result
    }
    return (pickQuestions(scored.filter { it.type in fastTypes }, fastMinutes) +
            pickQuestions(scored.filter { it.type in midTypes }, midMinutes) +
            pickQuestions(scored.filter { it.type in slowTypes }, slowMinutes)).shuffled()
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamSetupDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var selectedBankIds by remember { mutableStateOf(vm.questionBanks.map { it.id }.toSet()) }
    var totalMinutes by remember { mutableStateOf("60") }
    var selectedTypes by remember { mutableStateOf(QuestionType.entries.toSet()) }
    var showExamSession by remember { mutableStateOf(false) }
    var examQuestions by remember { mutableStateOf(listOf<Question>()) }

    if (showExamSession && examQuestions.isNotEmpty()) {
        ExamSessionPage(vm = vm, questions = examQuestions,
            totalMinutes = totalMinutes.toIntOrNull() ?: 60,
            onFinish = { showExamSession = false; onDismiss() })
        return
    }

    FullScreenDialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("考一考", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.ArrowBack, contentDescription = "返回") } })
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("选择题库", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                vm.questionBanks.forEach { bank ->
                    val count = vm.getQuestionsForBank(bank.id).size
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        selectedBankIds = if (bank.id in selectedBankIds) selectedBankIds - bank.id else selectedBankIds + bank.id
                    }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bank.id in selectedBankIds,
                            onCheckedChange = { selectedBankIds = if (it) selectedBankIds + bank.id else selectedBankIds - bank.id })
                        Text("${bank.subject.emoji} ${bank.name} (${count}题)", fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(16.dp))
                Text("考试时间", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberStepper(value = totalMinutes, onValueChange = { totalMinutes = it }, min = 10, max = 180, suffix = "")
                    Spacer(Modifier.width(8.dp))
                    Text("分钟", fontSize = 14.sp, color = Color.Gray)
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(); Spacer(Modifier.height(16.dp))
                Text("题型选择", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    QuestionType.entries.forEach { type ->
                        FilterChip(selected = type in selectedTypes,
                            onClick = { selectedTypes = if (type in selectedTypes) selectedTypes - type else selectedTypes + type },
                            label = { Text(type.label, fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                val allQ = selectedBankIds.flatMap { vm.getQuestionsForBank(it) }
                val minutes = totalMinutes.toIntOrNull() ?: 60
                val previewQ = generateExamQuestions(allQ, minutes, selectedTypes)
                Text("预估题数: ${previewQ.size} 题（可用: ${allQ.count { it.type in selectedTypes }}）", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { examQuestions = generateExamQuestions(allQ, minutes, selectedTypes); if (examQuestions.isNotEmpty()) showExamSession = true },
                    enabled = previewQ.isNotEmpty(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00))
                ) { Text("开始考试", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(48.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamSessionPage(vm: PsyMapViewModel, questions: List<Question>, totalMinutes: Int, onFinish: () -> Unit) {
    var currentIndex by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var selectedOptions by remember { mutableStateOf(mutableMapOf<String, Set<Int>>()) }
    var submitted by remember { mutableStateOf(setOf<String>()) }
    var correctSet by remember { mutableStateOf(setOf<String>()) }
    var handwritingImagesMap by remember { mutableStateOf(mutableMapOf<String, List<String>>()) }
    var showResult by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 倒计时
    var remainingSeconds by remember { mutableStateOf(totalMinutes * 60L) }
    var warned10 by remember { mutableStateOf(false) }
    var warned5 by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0 && !showResult) {
            kotlinx.coroutines.delay(1000)
            remainingSeconds--
            if (remainingSeconds == 600L && !warned10) {
                warned10 = true
                android.widget.Toast.makeText(context, "⏰ 还剩10分钟！", android.widget.Toast.LENGTH_LONG).show()
            }
            if (remainingSeconds == 300L && !warned5) {
                warned5 = true
                android.widget.Toast.makeText(context, "⏰ 还剩5分钟！请抓紧时间！", android.widget.Toast.LENGTH_LONG).show()
            }
            if (remainingSeconds <= 0L) { showResult = true }
        }
    }

    val question = questions.getOrNull(currentIndex)
    val mm = remainingSeconds / 60
    val ss = remainingSeconds % 60
    val timeColor = when { remainingSeconds <= 300 -> Color(0xFFD32F2F); remainingSeconds <= 600 -> Color(0xFFFF9800); else -> Color.Gray }

    if (showResult) {
        // 提交所有未提交的答案
        LaunchedEffect(Unit) {
            questions.forEach { q ->
                if (q.id !in submitted) {
                    val isChoice = q.type == QuestionType.SINGLE_CHOICE || q.type == QuestionType.MULTI_CHOICE
                    if (isChoice) {
                        val sel = selectedOptions[q.id] ?: emptySet()
                        if (sel.isNotEmpty()) {
                            val userAns = sel.sorted().map { ('A' + it).toString() }.joinToString("")
                            val correctLetters = q.answer.trim().uppercase().replace(Regex("[,，\\s]+"), "").toCharArray().map { it.toString() }.toSet()
                            val isCorrect = sel.sorted().map { ('A' + it).toString() }.toSet() == correctLetters
                            vm.submitAnswer(q.id, userAns, isCorrect)
                            if (isCorrect) correctSet = correctSet + q.id
                            submitted = submitted + q.id
                        }
                    } else {
                        val userAns = answers[q.id] ?: ""
                        if (userAns.isNotBlank()) {
                            vm.submitAnswer(q.id, userAns, false)
                            submitted = submitted + q.id
                        }
                    }
                }
            }
        }
        // 结果页
        ExamResultPage(questions = questions, correctSet = correctSet, answeredIds = submitted,
            totalMinutes = totalMinutes,
            usedSeconds = (totalMinutes * 60L) - remainingSeconds, onFinish = onFinish)
        return
    }

    if (question == null) { showResult = true; return }
    val liveQ = vm.questions.find { it.id == question.id } ?: question
    val isChoice = question.type == QuestionType.SINGLE_CHOICE || question.type == QuestionType.MULTI_CHOICE
    val isMulti = question.type == QuestionType.MULTI_CHOICE
    val isSubmitted = question.id in submitted

    FullScreenDialog(onDismissRequest = {}, dismissOnBackPress = false) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${currentIndex + 1}/${questions.size}", fontSize = 16.sp)
                        Spacer(Modifier.width(12.dp))
                        Text("⏱ ${String.format("%02d:%02d", mm, ss)}", fontSize = 14.sp, color = timeColor, fontWeight = FontWeight.Bold)
                    }},
                    actions = {
                        TextButton(onClick = { showResult = true }) { Text("交卷", color = Color(0xFFD32F2F), fontWeight = FontWeight.Bold) }
                    }
                )
            },
            bottomBar = {}
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                // 题型标签
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = {}, label = { Text(question.type.label, fontSize = 11.sp) })
                    Text("${question.type.minutesPerQuestion()}分钟/题", fontSize = 11.sp, color = Color.Gray,
                        modifier = Modifier.align(Alignment.CenterVertically))
                }
                Spacer(Modifier.height(12.dp))
                // 题目
                SimpleMarkdownText(question.content)
                Spacer(Modifier.height(16.dp))

                if (isChoice && question.options.isNotEmpty()) {
                    // 选择题
                    val curSel = selectedOptions[question.id] ?: emptySet()
                    if (isMulti) { Text("（多选题）", fontSize = 12.sp, color = Color.Gray); Spacer(Modifier.height(4.dp)) }
                    question.options.forEachIndexed { idx, opt ->
                        val label = ('A' + idx).toString()
                        val isSel = idx in curSel
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .border(1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
                            .clickable(enabled = !isSubmitted) {
                                val newSel = if (isMulti) { if (idx in curSel) curSel - idx else curSel + idx }
                                else { setOf(idx) }
                                selectedOptions = selectedOptions.toMutableMap().apply { put(question.id, newSel) }
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.White),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp)) {
                                Text("$label.", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text(opt, fontSize = 14.sp)
                            }
                        }
                    }
                } else {
                    // 主观题
                    val curAns = answers[question.id] ?: ""
                    val qImages = handwritingImagesMap[question.id] ?: emptyList()

                    OutlinedTextField(value = curAns, onValueChange = { answers = answers.toMutableMap().apply { put(question.id, it) } },
                        label = { Text("输入答案（或拍照手写答案）") }, modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = 15,
                        enabled = !isSubmitted)

                    // 显示已拍摄的手写图片缩略图
                    if (qImages.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            qImages.forEach { imgPath ->
                                val bmp = remember(imgPath) { android.graphics.BitmapFactory.decodeFile(imgPath) }
                                if (bmp != null) {
                                    Image(bitmap = bmp.asImageBitmap(), contentDescription = "手写答案",
                                        modifier = Modifier.size(80.dp).padding(2.dp).border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(4.dp)),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    var showPhotoPicker by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { showPhotoPicker = true }, enabled = !isSubmitted,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("拍照手写答案", fontSize = 13.sp)
                    }
                    if (showPhotoPicker) {
                        ExamPhotoCaptureWithSave(
                            questionId = question.id,
                            onResult = { ocrText, imagePath ->
                                // 保存图片路径
                                handwritingImagesMap = handwritingImagesMap.toMutableMap().apply {
                                    put(question.id, (get(question.id) ?: emptyList()) + imagePath)
                                }
                                // OCR 文字追加到答案
                                answers = answers.toMutableMap().apply {
                                    put(question.id, (get(question.id) ?: "") + "\n" + ocrText)
                                }
                                showPhotoPicker = false
                            },
                            onDismiss = { showPhotoPicker = false }
                        )
                    }
                }

                // 提交当前题
                if (!isSubmitted) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        submitted = submitted + question.id
                        if (isChoice) {
                            val sel = selectedOptions[question.id] ?: emptySet()
                            val userAns = sel.sorted().map { ('A' + it).toString() }.joinToString("")
                            val correctLetters = question.answer.trim().uppercase().replace(Regex("[,，\\s]+"), "").toCharArray().map { it.toString() }.toSet()
                            val isCorrect = sel.sorted().map { ('A' + it).toString() }.toSet() == correctLetters
                            vm.submitAnswer(question.id, userAns, isCorrect)
                            if (isCorrect) correctSet = correctSet + question.id
                        } else {
                            val userAns = answers[question.id] ?: ""
                            val hasImages = (handwritingImagesMap[question.id] ?: emptyList()).isNotEmpty()
                            if (hasImages) {
                                // 手写答案评分（含卷面评价）
                                val imgCount = (handwritingImagesMap[question.id] ?: emptyList()).size
                                val (score, feedback) = gradeHandwrittenAnswer(userAns, liveQ.answer, imgCount, vm.aiEnabled)
                                vm.aiGradeScore = score
                                vm.aiGradeResult = feedback
                                val isCorrect = score >= 60
                                vm.submitAnswer(question.id, userAns, isCorrect)
                                if (isCorrect) correctSet = correctSet + question.id
                            } else {
                                vm.gradeSubjectiveAnswer(liveQ, userAns)
                                if (vm.aiGradeScore >= 60) correctSet = correctSet + question.id
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("提交本题") }
                }

                // 答对后1秒自动进入下一题
                if (isSubmitted && question.id in correctSet) {
                    LaunchedEffect(question.id) {
                        kotlinx.coroutines.delay(1000)
                        if (currentIndex < questions.size - 1) currentIndex++ else showResult = true
                    }
                }

                // 已提交显示结果
                if (isSubmitted) {
                    Spacer(Modifier.height(12.dp))
                    val isCorrect = question.id in correctSet
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel, contentDescription = null,
                                tint = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFD32F2F))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isCorrect) "正确" else "错误", fontWeight = FontWeight.Medium)
                        }
                    }
                    // 显示答案和解析
                    if (liveQ.answer.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📝 参考答案", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFEF6C00))
                                Spacer(Modifier.height(4.dp))
                                SimpleMarkdownText(liveQ.answer)
                            }
                        }
                    }
                    if (liveQ.explanation.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("💡 解析", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF7B1FA2))
                                Spacer(Modifier.height(4.dp))
                                SimpleMarkdownText(liveQ.explanation)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                // 上一题/下一题导航按钮
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentIndex > 0) {
                        OutlinedButton(onClick = { currentIndex-- }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp)); Text("上一题")
                        }
                    }
                    Button(onClick = {
                        if (currentIndex < questions.size - 1) currentIndex++ else showResult = true
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text(if (currentIndex < questions.size - 1) "下一题" else "交卷")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamResultPage(questions: List<Question>, correctSet: Set<String>, answeredIds: Set<String>, totalMinutes: Int, usedSeconds: Long, onFinish: () -> Unit) {
    val totalCount = questions.size
    val answeredCount = answeredIds.size
    val unansweredCount = totalCount - answeredCount
    val correctCount = correctSet.size
    val wrongCount = answeredCount - correctCount
    val accuracy = if (answeredCount > 0) (correctCount.toFloat() / answeredCount * 100).toInt() else 0
    val usedMm = usedSeconds / 60
    val usedSs = usedSeconds % 60

    FullScreenDialog(onDismissRequest = {}, dismissOnBackPress = false) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("考试结果", fontWeight = FontWeight.Bold) }) }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(80.dp),
                    tint = if (accuracy >= 60) Color(0xFFFF9800) else Color(0xFF9E9E9E))
                Spacer(Modifier.height(16.dp))
                Text("考试完成！", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$totalCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("总题数", fontSize = 12.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$correctCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                Text("正确", fontSize = 12.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$wrongCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                Text("错误", fontSize = 12.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$unansweredCount", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9E9E9E))
                                Text("未作答", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("正确率: $accuracy%（已作答 $answeredCount 题）", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = Color(0xFFFF9800), modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(12.dp))
                        Text("用时: ${usedMm}分${usedSs}秒 / ${totalMinutes}分钟", fontSize = 13.sp, color = Color.Gray)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("完成", fontSize = 16.sp)
                }
            }
        }
    }
}

/** 考试中拍照OCR录入答案 */
@Composable
fun ExamPhotoCapture(onText: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var recognizing by remember { mutableStateOf(false) }
    val photoFile = remember { java.io.File(context.cacheDir, "exam_photo.jpg").apply { if (!exists()) createNewFile() } }
    val photoUri = remember { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            recognizing = true
            val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath) ?: run { onDismiss(); return@rememberLauncherForActivityResult }
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
            recognizer.process(image)
                .addOnSuccessListener { visionText -> recognizing = false; onText(visionText.text) }
                .addOnFailureListener { recognizing = false; android.widget.Toast.makeText(context, "识别失败", android.widget.Toast.LENGTH_SHORT).show(); onDismiss() }
        } else { onDismiss() }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            recognizing = true
            val bitmap = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri)) ?: run { onDismiss(); return@rememberLauncherForActivityResult }
            val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
            val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
            recognizer.process(image)
                .addOnSuccessListener { visionText -> recognizing = false; onText(visionText.text) }
                .addOnFailureListener { recognizing = false; onDismiss() }
        } else { onDismiss() }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(photoUri) else onDismiss()
    }

    if (recognizing) {
        AlertDialog(onDismissRequest = {}, title = { Text("识别中...") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text("正在识别手写内容...")
            }}, confirmButton = {})
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("拍照录入答案") },
            text = { Column {
                TextButton(onClick = { permLauncher.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("拍照")
                }
                TextButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("从相册选择")
                }
            }}, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
    }
}

/** 考试中拍照 — 保存图片 + OCR 识别 */
@Composable
fun ExamPhotoCaptureWithSave(questionId: String, onResult: (ocrText: String, imagePath: String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var recognizing by remember { mutableStateOf(false) }
    val imgDir = remember { java.io.File(context.getExternalFilesDir(null), "exam_images").apply { mkdirs() } }
    val photoFile = remember { java.io.File(context.cacheDir, "exam_hw_${System.currentTimeMillis()}.jpg").apply { if (!exists()) createNewFile() } }
    val photoUri = remember { androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile) }

    fun processImage(bitmap: android.graphics.Bitmap) {
        recognizing = true
        val savedFile = java.io.File(imgDir, "hw_${questionId}_${System.currentTimeMillis()}.jpg")
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, savedFile.outputStream())

        // 优先用腾讯云手写OCR（更准确），失败则用ML Kit
        Thread {
            val tencentResult = tencentHandwritingOcr(savedFile)
            if (tencentResult != null && tencentResult.isNotBlank()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    recognizing = false
                    onResult(tencentResult, savedFile.absolutePath)
                }
            } else {
                // 降级到 ML Kit
                val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions.Builder().build())
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        recognizing = false
                        onResult(visionText.text.ifBlank { "（识别结果为空）" }, savedFile.absolutePath)
                    }
                    .addOnFailureListener {
                        recognizing = false
                        onResult("（OCR识别失败）", savedFile.absolutePath)
                    }
            }
        }.start()
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val bmp = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath) ?: run { onDismiss(); return@rememberLauncherForActivityResult }
            processImage(bmp)
        } else onDismiss()
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val bmp = android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri)) ?: run { onDismiss(); return@rememberLauncherForActivityResult }
            processImage(bmp)
        } else onDismiss()
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(photoUri) else onDismiss()
    }

    if (recognizing) {
        AlertDialog(onDismissRequest = {}, title = { Text("识别中...") },
            text = { Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp)); Text("正在保存图片并识别手写内容...")
            }}, confirmButton = {})
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text("📸 拍照手写答案") },
            text = { Column {
                Text("拍摄纸上手写的答案，图片将保存并用OCR识别", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { permLauncher.launch(android.Manifest.permission.CAMERA) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("拍照")
                }
                TextButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("从相册选择")
                }
            }}, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
    }
}

/** 手写答案评分（AI开启时用AI评分，否则本地关键词） */
fun gradeHandwrittenAnswer(ocrText: String, correctAnswer: String, imageCount: Int, aiEnabled: Boolean = false): Pair<Int, String> {
    if (ocrText.isBlank() || ocrText.contains("OCR识别失败") || ocrText.contains("识别结果为空")) {
        return Pair(0, "❌ 未能识别手写内容，请确保字迹清晰、光线充足")
    }

    // AI 评分（更准确）+ 卷面分
    if (aiEnabled && correctAnswer.isNotBlank()) {
        var aiScore = -1
        var aiFeedback = ""
        val latch = java.util.concurrent.CountDownLatch(1)
        AiService.gradeSubjectiveAnswer(
            question = "",
            correctAnswer = correctAnswer,
            userAnswer = ocrText,
            onResult = { score, feedback ->
                // 内容分占80%，卷面分占20%
                val charCount = ocrText.replace(Regex("\\s+"), "").length
                val lineCount = ocrText.lines().filter { it.isNotBlank() }.size
                val neatnessScore = when {
                    charCount > 80 && lineCount > 3 -> 18
                    charCount > 50 -> 15
                    charCount > 30 -> 12
                    charCount > 15 -> 8
                    else -> 5
                }
                val contentScore = (score * 0.8).toInt()
                aiScore = (contentScore + neatnessScore).coerceIn(0, 100)
                val neatLabel = when { neatnessScore >= 15 -> "字迹清晰" ; neatnessScore >= 10 -> "基本可读" ; else -> "字迹较潦草" }
                aiFeedback = "🤖 AI评分: ${aiScore}分\n$feedback\n📝 卷面分: $neatnessScore/20（$neatLabel）\n🎯 总分 = 内容${contentScore} + 卷面${neatnessScore}"
                latch.countDown()
            },
            onError = { latch.countDown() }
        )
        latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        if (aiScore >= 0) return Pair(aiScore, aiFeedback)
    }

    // 本地关键词评分（降级方案）
    val punctuationRegex = Regex("[，。、；：\\u201c\\u201d\\u2018\\u2019（）\\[\\]【】\\s\\n\\r.,;:\"'()]+")
    val feedback = StringBuilder()

    val charCount = ocrText.replace(Regex("\\s+"), "").length
    val lineCount = ocrText.lines().filter { it.isNotBlank() }.size
    val neatnessScore = when {
        charCount > 50 -> 10; charCount > 30 -> 7; charCount > 10 -> 5; else -> 3
    }
    feedback.appendLine("📝 卷面: ${if (neatnessScore >= 8) "字迹清晰" else if (neatnessScore >= 5) "基本可读" else "字迹较潦草"} (${neatnessScore}/10)")

    if (correctAnswer.isBlank()) {
        feedback.appendLine("⚠️ 无标准答案，仅评价卷面")
        return Pair(neatnessScore * 10, feedback.toString())
    }

    val correctKeywords = correctAnswer.replace(punctuationRegex, " ").split(" ").filter { it.length >= 2 }.distinct()
    val userText = ocrText.replace(punctuationRegex, " ")
    if (correctKeywords.isEmpty()) return Pair(neatnessScore * 10, feedback.toString())

    val matchedCount = correctKeywords.count { userText.contains(it, ignoreCase = true) }
    val coverageRate = matchedCount.toFloat() / correctKeywords.size
    val contentScore = (coverageRate * 80).toInt()
    val totalScore = (contentScore + neatnessScore * 2).coerceIn(0, 100)

    val matched = correctKeywords.filter { userText.contains(it, ignoreCase = true) }
    val missed = correctKeywords.filter { !userText.contains(it, ignoreCase = true) }
    feedback.appendLine("📊 踩分点: ${matchedCount}/${correctKeywords.size}")
    if (matched.isNotEmpty()) feedback.appendLine("✅ 命中: ${matched.take(8).joinToString("、")}")
    if (missed.isNotEmpty()) feedback.appendLine("❌ 缺失: ${missed.take(8).joinToString("、")}")
    feedback.appendLine("🎯 总分: $totalScore (内容${contentScore} + 卷面${neatnessScore * 2})")
    return Pair(totalScore, feedback.toString())
}

/** 腾讯云手写OCR（免费1000次/月） */
private fun tencentHandwritingOcr(imageFile: java.io.File): String? {
    val secretId = TencentConfig.secretId
    val secretKey = TencentConfig.secretKey
    val host = "ocr.tencentcloudapi.com"
    val service = "ocr"
    val timestamp = System.currentTimeMillis() / 1000
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    val date = sdf.format(java.util.Date(timestamp * 1000))

    val imageBase64 = android.util.Base64.encodeToString(imageFile.readBytes(), android.util.Base64.NO_WRAP)
    val payload = """{"ImageBase64":"$imageBase64"}"""

    fun sha256(data: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(data)
    fun sha256Hex(data: ByteArray): String = sha256(data).joinToString("") { "%02x".format(it) }
    fun hmac256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    val payloadBytes = payload.toByteArray(Charsets.UTF_8)
    val hashedPayload = sha256Hex(payloadBytes)
    val canonicalRequest = "POST\n/\n\ncontent-type:application/json\nhost:$host\n\ncontent-type;host\n$hashedPayload"
    val credentialScope = "$date/$service/tc3_request"
    val stringToSign = "TC3-HMAC-SHA256\n$timestamp\n$credentialScope\n${sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))}"

    val secretDate = hmac256("TC3$secretKey".toByteArray(Charsets.UTF_8), date.toByteArray(Charsets.UTF_8))
    val secretService = hmac256(secretDate, service.toByteArray(Charsets.UTF_8))
    val secretSigning = hmac256(secretService, "tc3_request".toByteArray(Charsets.UTF_8))
    val signature = hmac256(secretSigning, stringToSign.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    val authorization = "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, SignedHeaders=content-type;host, Signature=$signature"

    return try {
        val request = okhttp3.Request.Builder()
            .url("https://$host")
            .addHeader("Content-Type", "application/json")
            .addHeader("Host", host)
            .addHeader("X-TC-Action", "GeneralHandwritingOCR")
            .addHeader("X-TC-Version", "2018-11-19")
            .addHeader("X-TC-Timestamp", timestamp.toString())
            .addHeader("X-TC-Region", "ap-beijing")
            .addHeader("Authorization", authorization)
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), payloadBytes))
            .build()
        val client = okhttp3.OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null
        val map = com.google.gson.Gson().fromJson<Map<String, Any>>(body, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
        @Suppress("UNCHECKED_CAST")
        val resp = map["Response"] as? Map<String, Any> ?: return null
        @Suppress("UNCHECKED_CAST")
        val error = resp["Error"] as? Map<String, Any>
        if (error != null) { android.util.Log.e("PsyMap-OCR", "腾讯OCR: ${error["Code"]} - ${error["Message"]}"); return null }
        @Suppress("UNCHECKED_CAST")
        val textDetections = resp["TextDetections"] as? List<Map<String, Any>> ?: return null
        textDetections.joinToString("\n") { (it["DetectedText"] as? String) ?: "" }.trim().ifBlank { null }
    } catch (e: Exception) {
        android.util.Log.e("PsyMap-OCR", "腾讯OCR异常: ${e.message}")
        null
    }
}


// ==================== Markdown 渲染工具 ====================

@Composable
fun SimpleMarkdownText(text: String, modifier: Modifier = Modifier) {
    // Markdown 渲染：支持 **粗体**、### 标题、- 列表、![图片](url) 图文混排
    val lines = text.split("\n")
    val imageRegex = Regex("!\\[([^]]*)]\\(([^)]+)\\)")
    Column(modifier = modifier) {
        lines.forEach { line ->
            val trimmed = line.trim()
            // 检查是否包含图片
            val imageMatch = imageRegex.find(trimmed)
            when {
                // 纯图片行
                imageMatch != null && imageMatch.range.first == 0 && imageMatch.range.last == trimmed.length - 1 -> {
                    val url = imageMatch.groupValues[2]
                    val alt = imageMatch.groupValues[1]
                    if (url.startsWith("http")) {
                        coil.compose.AsyncImage(
                            model = url,
                            contentDescription = alt,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                        )
                    } else if (url.startsWith("/") || url.startsWith("file:")) {
                        // 本地文件路径
                        val file = java.io.File(url.removePrefix("file://"))
                        if (file.exists()) {
                            val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = alt,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                                )
                            }
                        }
                    }
                }
                // 图文混排行（文字中嵌入图片）
                imageMatch != null -> {
                    // 先显示图片前的文字
                    val before = trimmed.substring(0, imageMatch.range.first)
                    if (before.isNotBlank()) Text(renderInlineMarkdown(before), fontSize = 14.sp, lineHeight = 20.sp)
                    val url = imageMatch.groupValues[2]
                    if (url.startsWith("http")) {
                        coil.compose.AsyncImage(
                            model = url, contentDescription = null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                        )
                    }
                    val after = trimmed.substring(imageMatch.range.last + 1)
                    if (after.isNotBlank()) Text(renderInlineMarkdown(after), fontSize = 14.sp, lineHeight = 20.sp)
                }
                trimmed.startsWith("### ") -> Text(trimmed.removePrefix("### "), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(vertical = 2.dp))
                trimmed.startsWith("## ") -> Text(trimmed.removePrefix("## "), fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(vertical = 3.dp))
                trimmed.startsWith("# ") -> Text(trimmed.removePrefix("# "), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Text("  • ${trimmed.drop(2)}", fontSize = 14.sp, lineHeight = 20.sp)
                trimmed.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> Text(text = renderInlineMarkdown(trimmed), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

fun renderInlineMarkdown(text: String): String {
    // 简单去除 **粗体** 标记，保留文字
    return text.replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
        .replace(Regex("__(.*?)__"), "$1")
        .replace(Regex("\\*(.*?)\\*"), "$1")
        .replace(Regex("_(.*?)_"), "$1")
        .replace(Regex("`(.*?)`"), "$1")
}



// ==================== TTS 引擎（腾讯云优先，Edge TTS 备用） ====================

private fun tencentTtsWithFallback(text: String, voice: VoiceOption): ByteArray? {
    // 分段（每段100字）
    val maxChunk = 100
    val chunks = mutableListOf<String>()
    var offset = 0
    while (offset < text.length) {
        val end = (offset + maxChunk).coerceAtMost(text.length)
        chunks.add(text.substring(offset, end))
        offset = end
    }

    val allAudio = java.io.ByteArrayOutputStream()
    var tencentFailed = false

    for ((idx, chunk) in chunks.withIndex()) {
        if (!tencentFailed) {
            val audioBytes = tencentTtsChunk(chunk, voice.tencentId)
            if (audioBytes != null) {
                allAudio.write(audioBytes)
                android.util.Log.d("PsyMap-TTS", "腾讯云 chunk ${idx+1}/${chunks.size}: ${audioBytes.size} bytes")
                continue
            }
            android.util.Log.w("PsyMap-TTS", "腾讯云失败，切换 Edge TTS (${voice.edgeVoice})")
            tencentFailed = true
        }
        val audioBytes = edgeTtsChunk(chunk, voice.edgeVoice)
        if (audioBytes != null) allAudio.write(audioBytes)
    }
    return if (allAudio.size() > 0) allAudio.toByteArray() else null
}

private fun tencentTtsChunk(text: String, voiceType: Int): ByteArray? {
    val secretId = TencentConfig.secretId
    val secretKey = TencentConfig.secretKey
    val host = "tts.tencentcloudapi.com"
    val service = "tts"
    val timestamp = System.currentTimeMillis() / 1000
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    val date = sdf.format(java.util.Date(timestamp * 1000))
    val sessionId = "s${System.nanoTime()}"

    val cleanText = text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    val payload = """{"Text":"$cleanText","SessionId":"$sessionId","VoiceType":$voiceType,"PrimaryLanguage":1,"SampleRate":16000,"Codec":"mp3","Speed":0,"Volume":5}"""

    fun sha256(data: ByteArray): ByteArray = java.security.MessageDigest.getInstance("SHA-256").digest(data)
    fun sha256Hex(data: ByteArray): String = sha256(data).joinToString("") { "%02x".format(it) }
    fun hmac256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    val payloadBytes = payload.toByteArray(Charsets.UTF_8)
    val hashedPayload = sha256Hex(payloadBytes)
    val canonicalRequest = "POST\n/\n\ncontent-type:application/json\nhost:$host\n\ncontent-type;host\n$hashedPayload"
    val credentialScope = "$date/$service/tc3_request"
    val stringToSign = "TC3-HMAC-SHA256\n$timestamp\n$credentialScope\n${sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))}"

    val secretDate = hmac256("TC3$secretKey".toByteArray(Charsets.UTF_8), date.toByteArray(Charsets.UTF_8))
    val secretService = hmac256(secretDate, service.toByteArray(Charsets.UTF_8))
    val secretSigning = hmac256(secretService, "tc3_request".toByteArray(Charsets.UTF_8))
    val signature = hmac256(secretSigning, stringToSign.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    val authorization = "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, SignedHeaders=content-type;host, Signature=$signature"

    return try {
        val request = okhttp3.Request.Builder()
            .url("https://$host")
            .addHeader("Content-Type", "application/json")
            .addHeader("Host", host)
            .addHeader("X-TC-Action", "TextToVoice")
            .addHeader("X-TC-Version", "2019-08-23")
            .addHeader("X-TC-Timestamp", timestamp.toString())
            .addHeader("Authorization", authorization)
            .post(okhttp3.RequestBody.create("application/json".toMediaType(), payloadBytes))
            .build()
        val client = okhttp3.OkHttpClient.Builder().connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return null
        val map = com.google.gson.Gson().fromJson<Map<String, Any>>(body, object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type)
        @Suppress("UNCHECKED_CAST")
        val resp = map["Response"] as? Map<String, Any> ?: return null
        @Suppress("UNCHECKED_CAST")
        val error = resp["Error"] as? Map<String, Any>
        if (error != null) { android.util.Log.e("PsyMap-TTS", "腾讯云: ${error["Code"]} - ${error["Message"]}"); return null }
        val audio = resp["Audio"] as? String ?: return null
        android.util.Base64.decode(audio, android.util.Base64.DEFAULT)
    } catch (e: Exception) { android.util.Log.e("PsyMap-TTS", "腾讯云异常: ${e.message}"); null }
}

private fun edgeTtsChunk(text: String, voice: String = "zh-CN-XiaoxiaoNeural"): ByteArray? {
    val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='zh-CN'>" +
        "<voice name='$voice'><prosody rate='+0%' volume='+0%'>${text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")}</prosody></voice></speak>"
    val connectId = java.util.UUID.randomUUID().toString().replace("-", "")
    val url = "wss://speech.platform.bing.com/consumer/speech/synthesize/readaloud/edge/v1?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&ConnectionId=$connectId"

    val client = okhttp3.OkHttpClient.Builder().connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
    val audioBuffer = java.io.ByteArrayOutputStream()
    var completed = false; var error = false

    val ws = client.newWebSocket(okhttp3.Request.Builder().url(url)
        .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36").build(),
        object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                webSocket.send("Content-Type:application/json; charset=utf-8\r\nPath:speech.config\r\n\r\n" +
                    """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"audio-24khz-48kbitrate-mono-mp3"}}}}""")
                webSocket.send("X-RequestId:$connectId\r\nContent-Type:application/ssml+xml\r\nPath:ssml\r\n\r\n$ssml")
            }
            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) { if (text.contains("Path:turn.end")) { completed = true; webSocket.close(1000, "done") } }
            override fun onMessage(webSocket: okhttp3.WebSocket, bytes: okio.ByteString) {
                val data = bytes.toByteArray()
                if (data.size > 2) { val hl = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF); if (hl + 2 < data.size) audioBuffer.write(data, hl + 2, data.size - hl - 2) }
            }
            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) { error = true }
            override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) { completed = true }
        })
    var waited = 0; while (!completed && !error && waited < 600) { Thread.sleep(100); waited++ }
    client.dispatcher.executorService.shutdown()
    return if (audioBuffer.size() > 0) audioBuffer.toByteArray() else null
}
