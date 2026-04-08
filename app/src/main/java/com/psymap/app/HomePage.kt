package com.psymap.app

import android.content.ContentResolver
import android.content.Intent
import android.graphics.Bitmap
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
    var showStats by remember { mutableStateOf(false) }
    var showCalendar by remember { mutableStateOf(false) }
    var showCreateBank by remember { mutableStateOf(false) }
    var showMakeAudio by remember { mutableStateOf(false) }
    var showListenAudio by remember { mutableStateOf(false) }
    var showMoreKnowledge by remember { mutableStateOf(false) }
    var pendingFileUri by remember { mutableStateOf<Uri?>(null) }
    var showFileImportDialog by remember { mutableStateOf(false) }
    var showStudyPlan by remember { mutableStateOf(false) }
    var clickedQuestion by remember { mutableStateOf<Question?>(null) }
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
                onMyBanks = { searchText = ""; vm.searchQuestions("") },
                onMakeAudio = { showMakeAudio = true },
                onListen = { showListenAudio = true },
                onMoreKnowledge = { showMoreKnowledge = true }
            )
        }

        // 搜索结果
        if (searchText.isNotEmpty() && vm.searchResults.isNotEmpty()) {
            item { Text("搜索结果 (${vm.searchResults.size})", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Medium) }
            items(vm.searchResults.take(20)) { question -> SearchResultItem(question, vm) { clickedQuestion = question } }
        } else {
            // 我的题库
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("我的题库", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (vm.isAdmin) {
                        Row {
                            TextButton(onClick = {
                                filePickerLauncher.launch(arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                    "text/plain",
                                    "image/*"
                                ))
                            }) { Text("📄 导入文件", color = Color.Gray, fontSize = 13.sp) }
                            TextButton(onClick = { showCreateBank = true }) {
                                Text("+ 创建题库", color = MaterialTheme.colorScheme.primary)
                            }
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
    if (showStats) {
        // removed
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
    if (showMoreKnowledge) {
        MoreKnowledgeDialog(vm = vm, onDismiss = { showMoreKnowledge = false })
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
    onMyBanks: () -> Unit, onMakeAudio: () -> Unit,
    onListen: () -> Unit, onMoreKnowledge: () -> Unit
) {
    val actions = listOf(
        Triple(Icons.Default.Schedule, "学习计划", onStudyPlan),
        Triple(Icons.Default.CalendarMonth, "打卡日历", onCalendar),
        Triple(Icons.Default.ErrorOutline, "复习错题", onWrongBook),
        Triple(Icons.Default.Star, "收藏题目", onFavorites),
        Triple(Icons.Default.LibraryBooks, "我的题库", onMyBanks),
        Triple(Icons.Default.RecordVoiceOver, "制作音频", onMakeAudio),
        Triple(Icons.Default.Headphones, "磨耳朵", onListen),
        Triple(Icons.Default.MenuBook, "更多知识", onMoreKnowledge)
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        for (row in actions.chunked(4)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for ((icon, label, onClick) in row) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 10.dp).width(72.dp)) {
                        Box(modifier = Modifier.size(46.dp).background(Color(0xFFFFF3E0), CircleShape),
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
    val stats = vm.getSubjectStats()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学习统计") },
        text = {
            Column {
                Text("总题目: ${vm.questions.size}  |  错题: ${vm.getWrongQuestions().size}  |  收藏: ${vm.getFavoriteQuestions().size}",
                    fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                stats.forEach { (subject, pair) ->
                    val (correct, total) = pair
                    val rate = if (total > 0) correct.toFloat() / total else 0f
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${subject.emoji} ${subject.label}", fontSize = 13.sp, modifier = Modifier.width(72.dp))
                        LinearProgressIndicator(progress = { rate }, modifier = Modifier.weight(1f).height(8.dp),
                            color = MaterialTheme.colorScheme.primary, trackColor = Color(0xFFE0E0E0))
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
        Dialog(
            onDismissRequest = { showStudySession = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            StudySessionPage(vm = vm, onFinish = { showStudySession = false })
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("复习错题", fontWeight = FontWeight.Bold) },
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
        Dialog(
            onDismissRequest = { showStudySession = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            StudySessionPage(vm = vm, onFinish = { showStudySession = false })
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("收藏题目", fontWeight = FontWeight.Bold) },
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
                            Text("暂无收藏题目", color = Color(0xFF999999), fontSize = 15.sp)
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

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        // 新建题库
                        val bankId = if (isCreatingNew && newBankName.isNotBlank()) {
                            vm.createBank(newBankName, newBankSubject).id
                        } else selectedBankId

                        if (bankId.isBlank()) {
                            Toast.makeText(context, "请选择或新建题库", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // 读取文件内容
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
                                    return@Button
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
                                    return@Button
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
                                    return@Button
                                }
                                vm.importFromFileContent(text, bankId, selectedType, tagFrequent, tagMemorize)
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "文件读取失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isLoading && (selectedBankId.isNotBlank() || (isCreatingNew && newBankName.isNotBlank())),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("开始导入") }
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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
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
                                            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
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
                Text("设定每个题库每天要复习的题目数量\n保存后将自动创建日历提醒（每天16:00）", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(12.dp))
                vm.questionBanks.forEach { bank ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${bank.subject.emoji} ${bank.name}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        OutlinedTextField(
                            value = targets[bank.id] ?: "10",
                            onValueChange = { targets[bank.id] = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(72.dp),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            suffix = { Text("题", fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val map = targets.mapValues { (it.value.toIntOrNull() ?: 10) }
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
data class VoiceOption(val id: String, val label: String, val voiceParam: String)
private val voiceOptions = listOf(
    VoiceOption("alex", "播音男声", "FunAudioLLM/CosyVoice2-0.5B:alex"),
    VoiceOption("bella", "清纯女声", "FunAudioLLM/CosyVoice2-0.5B:bella"),
    VoiceOption("claire", "干练女声", "FunAudioLLM/CosyVoice2-0.5B:claire"),
    VoiceOption("david", "磁性男声", "FunAudioLLM/CosyVoice2-0.5B:david")
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

    Dialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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
                            OutlinedTextField(
                                value = questionCount,
                                onValueChange = { questionCount = it.filter { c -> c.isDigit() } },
                                singleLine = true,
                                modifier = Modifier.width(72.dp),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                suffix = { Text("题", fontSize = 12.sp, color = Color(0xFFBDBDBD)) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                            )
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
                    progress = "初始化语音引擎..."
                    val bankQuestions = vm.getQuestionsForBank(selectedBankId)
                    val cnt = (questionCount.toIntOrNull() ?: 10).coerceAtMost(bankQuestions.size)
                    val selected = if (shuffle) bankQuestions.shuffled().take(cnt) else bankQuestions.take(cnt)
                    val bank = vm.questionBanks.find { it.id == selectedBankId }
                    val bankName = bank?.name ?: "题库"
                    val voiceParam = selectedVoice.voiceParam

                    scope.launch(Dispatchers.IO) {
                        val fullText = StringBuilder()
                        selected.forEachIndexed { idx, q ->
                            fullText.append("第${idx + 1}题。${q.content}。答案：${q.answer}。")
                        }
                        if (fullText.isEmpty()) {
                            withContext(Dispatchers.Main) { isGenerating = false; progress = "" }
                            return@launch
                        }

                        val dir = java.io.File(context.getExternalFilesDir(null), "audio")
                        dir.mkdirs()
                        val fileName = "${bankName}_${cnt}q.mp3"
                        val file = java.io.File(dir, fileName)

                        withContext(Dispatchers.Main) { progress = "正在生成音频（${selectedVoice.label}）..." }
                        var apiSuccess = false
                        try {
                            val body = com.google.gson.Gson().toJson(mapOf(
                                "model" to "FunAudioLLM/CosyVoice2-0.5B",
                                "input" to fullText.toString().take(5000),
                                "voice" to voiceParam,
                                "response_format" to "mp3"
                            ))
                            val response = okhttp3.OkHttpClient.Builder()
                                .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                                .build()
                                .newCall(okhttp3.Request.Builder()
                                    .url("${AiService.apiBaseUrl}/audio/speech")
                                    .addHeader("Authorization", "Bearer ${AiService.apiKey}")
                                    .addHeader("Content-Type", "application/json")
                                    .post(body.toRequestBody("application/json".toMediaType()))
                                    .build()).execute()
                            if (response.isSuccessful && response.body != null) {
                                response.body!!.byteStream().use { input ->
                                    file.outputStream().use { output -> input.copyTo(output) }
                                }
                                apiSuccess = file.length() > 100
                            }
                        } catch (_: Exception) {}

                        if (apiSuccess) {
                            withContext(Dispatchers.Main) {
                                isGenerating = false; progress = ""
                                Toast.makeText(context, "音频生成完成！可在磨耳朵中播放", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                            return@launch
                        }

                        withContext(Dispatchers.Main) {
                            progress = "API不可用，使用系统朗读..."
                            val tts = android.speech.tts.TextToSpeech(context) { status ->
                                if (status == android.speech.tts.TextToSpeech.SUCCESS) {}
                            }
                            delay(2000)
                            tts.language = java.util.Locale.CHINESE
                            tts.setSpeechRate(0.9f)
                            tts.speak(fullText.toString(), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "psymap")
                            val estimatedMs = (fullText.length * 250L).coerceIn(5000, 180000)
                            delay(estimatedMs)
                            tts.stop()
                            tts.shutdown()
                            isGenerating = false; progress = ""
                            Toast.makeText(context, "朗读完成", Toast.LENGTH_SHORT).show()
                            onDismiss()
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

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    Dialog(
        onDismissRequest = { mediaPlayer?.release(); onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("磨耳朵", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { mediaPlayer?.release(); onDismiss() }) {
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
                        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 48.dp)) {
                            if (isPlaying) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFFF8A00))
                                    Spacer(Modifier.width(8.dp))
                                    Text("正在播放: $currentPlaying", fontSize = 12.sp, color = Color(0xFFFF8A00))
                                }
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
                                        if (isPlaying) {
                                            mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer = null
                                            isPlaying = false; currentPlaying = ""
                                        } else {
                                            val filesToPlay = audioFiles.filter { it.name in selectedFiles }
                                            if (filesToPlay.isEmpty()) return@Button
                                            isPlaying = true
                                            fun playNext(index: Int) {
                                                if (index >= filesToPlay.size) { isPlaying = false; currentPlaying = ""; return }
                                                currentPlaying = filesToPlay[index].nameWithoutExtension
                                                mediaPlayer?.release()
                                                mediaPlayer = android.media.MediaPlayer().apply {
                                                    setDataSource(filesToPlay[index].absolutePath)
                                                    prepare(); start()
                                                    setOnCompletionListener { playNext(index + 1) }
                                                }
                                            }
                                            playNext(0)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = selectedFiles.isNotEmpty() || isPlaying,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00))
                                ) { Text(if (isPlaying) "⏹ 停止" else "▶ 播放", fontSize = 13.sp) }
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

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
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

// ==================== 更多知识（全屏 Tab 页） ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreKnowledgeDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("🧠 心理学知识", "📰 英文泛读")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("更多知识", fontWeight = FontWeight.Bold) },
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
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 14.sp) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> PsychologyKnowledgeContent(vm = vm)
                    1 -> EnglishReadingContent(vm = vm)
                }
            }
        }
    }
}

// ==================== 心理学知识 ====================
// 心理学知识 — 许燕《人格心理学》大纲
private val psyChapters = listOf(
    "经典精神分析学派" to "弗洛伊德的经典精神分析理论、荣格的分析心理学理论、阿德勒的个体心理学理论",
    "新精神分析学派" to "霍妮的人格理论、弗洛姆的人格理论、沙利文的人格理论、埃里克森的人格理论、客体关系理论",
    "行为主义学派" to "华生的人格理论、斯金纳的人格理论",
    "人本主义与积极心理学" to "马斯洛的人格理论、罗杰斯的人格理论、积极心理学",
    "人格特质理论" to "奥尔波特的特质理论、卡特尔的特质因素论、艾森克的人格理论、五因素模型",
    "认知与社会认知学派" to "凯利的个人建构理论、罗特的社会认知论、班杜拉的社会认知论"
)

// 心理学知识内容（供 tab 页使用）
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PsychologyKnowledgeContent(vm: PsyMapViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("psymap_psy_knowledge", android.content.Context.MODE_PRIVATE) }
    var articles by remember { mutableStateOf(loadPsyArticles(prefs)) }
    var isLoading by remember { mutableStateOf(false) }
    var userInput by remember { mutableStateOf("") }
    var selectedChapter by remember { mutableStateOf(psyChapters[0]) }
    var showChapterDropdown by remember { mutableStateOf(false) }
    val hasUserInput = userInput.isNotBlank()

    fun generate() {
        isLoading = true
        val prompt = if (hasUserInput) {
            """你是北师大心理学考研辅导专家。用户输入了题目，请润色题目并生成完整答案。
答案按踩分点逐条列出，每个踩分点2-3句展开，贴合北师大MAP考研判卷规则，使用markdown格式。
返回纯JSON数组：[{"title":"润色后的题目","answer":"## 答案\n\n### 1. 踩分点一\n内容..."}]
用户题目：$userInput"""
        } else {
            """你是北师大心理学考研辅导专家。针对「${selectedChapter.first}」（${selectedChapter.second}）生成3道论述题。
每道题含题目+完整答案（含踩分点），答案使用markdown格式。
返回纯JSON数组：[{"title":"题目","answer":"## 答案\n\n### 1. 踩分点一\n内容..."}]"""
        }
        val chapter = if (hasUserInput) "自定义题目" else selectedChapter.first
        AiService.chatCompletion(prompt, if (hasUserInput) userInput else "生成${chapter}论述题", { result ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                isLoading = false
                try {
                    val cleaned = result.replace(Regex("```(?:json)?\\s*"), "").replace(Regex("```\\s*"), "").trim()
                    val list = com.google.gson.Gson().fromJson<List<Map<String, String>>>(cleaned,
                        object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type)
                    if (list != null) {
                        val pinned = articles.filter { it["pinned"] == "true" && it["chapter"] == chapter }
                        val newItems = list.map { mutableMapOf("title" to (it["title"] ?: ""), "answer" to (it["answer"] ?: ""), "chapter" to chapter, "pinned" to "false") }
                        articles = articles.filter { it["chapter"] != chapter } + pinned + newItems
                        savePsyArticles(prefs, articles)
                    }
                } catch (_: Exception) {}
            }
        }, { _ -> android.os.Handler(android.os.Looper.getMainLooper()).post { isLoading = false } })
    }

    val displayChapter = if (hasUserInput) "自定义题目" else selectedChapter.first
    val chapterArticles = articles.filter { it["chapter"] == displayChapter }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // 用户输入框
        OutlinedTextField(value = userInput, onValueChange = { userInput = it },
            label = { Text("输入题目（论述题/案例分析/简答题）") },
            placeholder = { Text("如：试述弗洛伊德的人格结构理论及其临床意义") },
            modifier = Modifier.fillMaxWidth(), maxLines = 4)
        Spacer(Modifier.height(12.dp))

        // 学派下拉选择（输入框为空时可用）
        if (!hasUserInput) {
            ExposedDropdownMenuBox(expanded = showChapterDropdown, onExpandedChange = { showChapterDropdown = it }) {
                OutlinedTextField(value = selectedChapter.first, onValueChange = {}, readOnly = true,
                    label = { Text("选择学派") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showChapterDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true)
                ExposedDropdownMenu(expanded = showChapterDropdown, onDismissRequest = { showChapterDropdown = false }) {
                    psyChapters.forEach { ch ->
                        DropdownMenuItem(
                            text = { Column { Text(ch.first, fontSize = 14.sp); Text(ch.second, fontSize = 11.sp, color = Color(0xFF999999), maxLines = 1) } },
                            onClick = { selectedChapter = ch; showChapterDropdown = false },
                            leadingIcon = { if (selectedChapter == ch) Icon(Icons.Default.Check, null, tint = Color(0xFFEF6C00), modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // AI生成按钮
        Button(onClick = { generate() }, enabled = !isLoading, shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00))
        ) { Text(if (isLoading) "生成中..." else "AI 一键生成题目及答案", fontSize = 14.sp) }
        Spacer(Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color(0xFFFF8A00))
                    Spacer(Modifier.height(8.dp)); Text("AI正在生成...", fontSize = 13.sp, color = Color(0xFF999999))
                }
            }
        }
        if (chapterArticles.isEmpty() && !isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("输入题目或选择学派后点击生成", color = Color(0xFF999999), fontSize = 14.sp)
            }
        }

        chapterArticles.forEachIndexed { idx, article ->
            val isPinned = article["pinned"] == "true"
            var expanded by remember { mutableStateOf(false) }
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isPinned) Color(0xFFFFF8E1) else Color.White),
                elevation = CardDefaults.cardElevation(1.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("Q${idx + 1}. ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00))
                        Text(article["title"] ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val gi = articles.indexOf(article)
                            if (gi >= 0) { val u = articles.toMutableList(); u[gi] = article.toMutableMap().apply { put("pinned", if (isPinned) "false" else "true") }; articles = u; savePsyArticles(prefs, articles) }
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PushPin, null, tint = if (isPinned) Color(0xFFEF6C00) else Color(0xFFCCCCCC), modifier = Modifier.size(18.dp))
                        }
                    }
                    TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(if (expanded) "收起答案 ▲" else "查看答案 ▼", fontSize = 12.sp, color = Color(0xFF1976D2))
                    }
                    if (expanded) {
                        var showAddDialog by remember { mutableStateOf(false) }
                        HorizontalDivider(color = Color(0xFFF0F0F0), modifier = Modifier.padding(vertical = 4.dp))
                        SimpleMarkdownText(article["answer"] ?: "")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("加入题库", fontSize = 12.sp)
                        }
                        if (showAddDialog) {
                            AddToBankDialog(vm = vm, title = article["title"] ?: "", answer = article["answer"] ?: "",
                                defaultType = QuestionType.ESSAY, onDismiss = { showAddDialog = false })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// ==================== 加入题库选择弹窗（通用） ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddToBankDialog(
    vm: PsyMapViewModel,
    title: String,
    answer: String,
    defaultType: QuestionType = QuestionType.ESSAY,
    onDismiss: () -> Unit
) {
    var selectedBankId by remember { mutableStateOf(vm.questionBanks.firstOrNull()?.id ?: "") }
    val selectedBank = vm.questionBanks.find { it.id == selectedBankId }
    val availableTypes = selectedBank?.subject?.availableQuestionTypes() ?: QuestionType.entries.toList()
    var selectedType by remember { mutableStateOf(defaultType) }
    var tagFrequent by remember { mutableStateOf(false) }
    var tagMemorize by remember { mutableStateOf(true) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入题库") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("选择题库", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                vm.questionBanks.forEach { bank ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { selectedBankId = bank.id }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedBankId == bank.id, onClick = { selectedBankId = bank.id })
                        Spacer(Modifier.width(4.dp))
                        Text("${bank.subject.emoji} ${bank.name}", fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("题目分类", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    availableTypes.forEach { type ->
                        FilterChip(selected = selectedType == type, onClick = { selectedType = type },
                            label = { Text(type.label, fontSize = 11.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("标签", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tagFrequent, onClick = { tagFrequent = !tagFrequent },
                        label = { Text("🔥 常考", fontSize = 11.sp) })
                    FilterChip(selected = tagMemorize, onClick = { tagMemorize = !tagMemorize },
                        label = { Text("📖 多背", fontSize = 11.sp) })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (selectedBankId.isNotBlank()) {
                    vm.addQuestion(selectedBankId, title, answer, selectedType, isMemorize = tagMemorize)
                    val lastQ = vm.questions.lastOrNull()
                    if (lastQ != null && tagFrequent) vm.toggleFrequent(lastQ.id)
                    val bankName = vm.questionBanks.find { it.id == selectedBankId }?.name ?: ""
                    Toast.makeText(context, "已加入「$bankName」${selectedType.label}", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }, shape = RoundedCornerShape(8.dp)) { Text("加入题库") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun loadPsyArticles(prefs: android.content.SharedPreferences): List<Map<String, String>> {
    val json = prefs.getString("psy_articles", "[]") ?: "[]"
    return try {
        com.google.gson.Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type) ?: emptyList()
    } catch (_: Exception) { emptyList() }
}

private fun savePsyArticles(prefs: android.content.SharedPreferences, articles: List<Map<String, String>>) {
    prefs.edit().putString("psy_articles", com.google.gson.Gson().toJson(articles)).apply()
}

// ==================== 英文泛读 ====================
// 英文泛读内容（供 tab 页使用）
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnglishReadingContent(vm: PsyMapViewModel) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("psymap_reading", android.content.Context.MODE_PRIVATE)
    var articles by remember { mutableStateOf(loadSavedArticles(prefs)) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedArticle by remember { mutableStateOf<Map<String, String>?>(null) }

    fun fetchArticles() {
        isLoading = true
        val prompt = """你是考研英语阅读材料推荐专家。请推荐5篇适合考研英语阅读训练的英文文章。
来源期刊：The Economist, The Guardian, Scientific American, The New York Times, The Atlantic, Nature, Science
题材分布：心理学类1篇、政经类1篇、社会学类1篇、科技类1篇、文化教育类1篇。
严格要求：直接提供完整原文（400-600词），不要改写概括拼接，保留段落结构用\n\n分隔，提供中文翻译和URL。
返回纯JSON数组：[{"title":"标题","title_cn":"中文标题","source":"期刊 · 题材","url":"https://...","content_en":"原文","content_cn":"翻译"}]"""
        AiService.chatCompletion(prompt, "生成5篇英文泛读文章", { result ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                isLoading = false
                try {
                    val cleaned = result.replace(Regex("```(?:json)?\\s*"), "").replace(Regex("```\\s*"), "").trim()
                    val list = com.google.gson.Gson().fromJson<List<Map<String, String>>>(cleaned,
                        object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type)
                    val pinned = articles.filter { it["pinned"] == "true" }
                    articles = pinned + (list?.map { it.toMutableMap().apply { put("pinned", "false") } } ?: emptyList())
                    saveArticles(prefs, articles)
                } catch (_: Exception) {}
            }
        }, { _ -> android.os.Handler(android.os.Looper.getMainLooper()).post { isLoading = false } })
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 获取文章按钮
        Button(
            onClick = { fetchArticles() },
            enabled = !isLoading,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
        ) { Text(if (isLoading) "加载中..." else "获取文章", fontSize = 14.sp) }

        // 文章列表
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            if (articles.isEmpty() && !isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("点击上方按钮获取英文泛读文章", color = Color(0xFF999999), fontSize = 14.sp)
                }
            }
            articles.forEachIndexed { idx, article ->
                val isPinned = article["pinned"] == "true"
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedArticle = article },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isPinned) Color(0xFFFFF8E1) else Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(article["title"] ?: "", fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(article["title_cn"] ?: "", fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                            Text(article["source"] ?: "", fontSize = 11.sp, color = Color(0xFF1976D2))
                        }
                        IconButton(onClick = {
                            val updated = articles.toMutableList()
                            updated[idx] = article.toMutableMap().apply { put("pinned", if (isPinned) "false" else "true") }
                            articles = updated
                            saveArticles(prefs, articles)
                        }) {
                            Icon(Icons.Default.PushPin, contentDescription = "Pin",
                                tint = if (isPinned) Color(0xFFEF6C00) else Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    selectedArticle?.let { article ->
        ArticleDetailDialog(article = article, vm = vm, onDismiss = { selectedArticle = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ArticleDetailDialog(article: Map<String, String>, vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val prefs = remember { context.getSharedPreferences("psymap_marks", android.content.Context.MODE_PRIVATE) }
    val articleKey = (article["title"] ?: "").hashCode().toString()

    // 从持久化加载标记
    var markedWords by remember {
        val saved = prefs.getString("words_$articleKey", null)
        mutableStateOf(if (saved.isNullOrBlank()) emptyList() else saved.split("|||").filter { it.isNotBlank() })
    }
    var markedSentences by remember {
        val saved = prefs.getString("sents_$articleKey", null)
        mutableStateOf(if (saved.isNullOrBlank()) emptyList() else saved.split("|||").filter { it.isNotBlank() })
    }
    // 多选删除模式
    var deleteMode by remember { mutableStateOf(false) }
    var selectedForDelete by remember { mutableStateOf(setOf<String>()) }

    fun saveMarks() {
        prefs.edit()
            .putString("words_$articleKey", markedWords.joinToString("|||"))
            .putString("sents_$articleKey", markedSentences.joinToString("|||"))
            .apply()
    }

    fun getClipboardText(): String = clipboardManager.getText()?.text?.trim() ?: ""

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(article["title_cn"] ?: "", fontSize = 16.sp, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            },
            bottomBar = {
                // 底部操作栏：选中文字后点击标记
                Surface(shadowElevation = 8.dp, color = Color.White) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val text = getClipboardText()
                                if (text.isNotBlank() && text.length <= 50) {
                                    if (text !in markedWords) { markedWords = markedWords + text; saveMarks() }
                                    Toast.makeText(context, "已标记词汇「$text」", Toast.LENGTH_SHORT).show()
                                } else if (text.isBlank()) {
                                    Toast.makeText(context, "请先长按选中文字并复制", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "词汇过长，请用右侧按钮标记为长难句", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF6C00))
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("标记词汇", fontSize = 13.sp)
                        }
                        Button(
                            onClick = {
                                val text = getClipboardText()
                                if (text.isNotBlank()) {
                                    if (text !in markedSentences) { markedSentences = markedSentences + text; saveMarks() }
                                    Toast.makeText(context, "已标记长难句", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "请先长按选中文字并复制", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                        ) {
                            Icon(Icons.Default.FormatQuote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("标记长难句", fontSize = 13.sp)
                        }
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 来源 + URL
                Text(article["source"] ?: "", fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Medium)
                val url = article["url"] ?: ""
                if (url.isNotBlank()) {
                    Text("🔗 $url", fontSize = 11.sp, color = Color(0xFF1976D2), maxLines = 1,
                        modifier = Modifier.clickable {
                            try { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
                        },
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                }

                Spacer(Modifier.height(8.dp))
                Text("💡 长按选中文字 → 复制 → 点击底部按钮标记", fontSize = 11.sp, color = Color(0xFFBDBDBD))
                Spacer(Modifier.height(12.dp))

                // 可选中的文本区域
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Column {
                        // English 原文
                        Text("English", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                        Spacer(Modifier.height(8.dp))
                        val enContent = (article["content_en"] ?: "").replace("**", "")
                        val enParagraphs = enContent.split("\n\n").filter { it.isNotBlank() }
                        enParagraphs.forEachIndexed { idx, para ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (idx % 2 == 0) Color(0xFFF5F5F5) else Color(0xFFE8F5E9)
                                ),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Text(
                                    text = buildHighlightedText(para.trim(), markedWords),
                                    fontSize = 14.sp, lineHeight = 22.sp,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        if (enParagraphs.isEmpty()) {
                            Text(enContent, fontSize = 14.sp, lineHeight = 22.sp)
                        }

                        Spacer(Modifier.height(16.dp))

                        // 中文翻译
                        Text("中文翻译", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                        Spacer(Modifier.height(8.dp))
                        val cnParagraphs = (article["content_cn"] ?: "").split("\n\n").filter { it.isNotBlank() }
                        cnParagraphs.forEachIndexed { idx, para ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (idx % 2 == 0) Color(0xFFFFF8E1) else Color(0xFFFFF3E0)
                                ),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Text(para.trim(), fontSize = 14.sp, lineHeight = 22.sp,
                                    modifier = Modifier.padding(12.dp))
                            }
                        }
                        if (cnParagraphs.isEmpty()) {
                            Text(article["content_cn"] ?: "", fontSize = 14.sp, lineHeight = 22.sp)
                        }
                    }
                }

                // 标记的重点词汇（在 SelectionContainer 外）
                if (markedWords.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📝 重点词汇（点击加入题库）", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEF6C00), modifier = Modifier.weight(1f))
                        if (deleteMode) {
                            TextButton(onClick = {
                                markedWords = markedWords.filter { it !in selectedForDelete }
                                markedSentences = markedSentences.filter { it !in selectedForDelete }
                                saveMarks(); selectedForDelete = emptySet(); deleteMode = false
                            }) { Text("删除(${selectedForDelete.size})", color = Color(0xFFD32F2F), fontSize = 12.sp) }
                            TextButton(onClick = { deleteMode = false; selectedForDelete = emptySet() }) { Text("取消", fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        markedWords.forEach { word ->
                            var saved by remember { mutableStateOf(false) }
                            val isSelected = word in selectedForDelete
                            val bgColor = when {
                                deleteMode && isSelected -> Color(0xFFFFCDD2)
                                saved -> Color(0xFFE8F5E9)
                                else -> Color(0xFFFFF3E0)
                            }
                            Card(
                                modifier = Modifier.combinedClickable(
                                    onClick = {
                                        if (deleteMode) {
                                            selectedForDelete = if (isSelected) selectedForDelete - word else selectedForDelete + word
                                        } else {
                                            val engBank = vm.questionBanks.find { it.subject == Subject.ENGLISH }
                                            if (engBank != null) {
                                                vm.addQuestion(engBank.id, word, "", QuestionType.VOCAB_PHRASE, isMemorize = true)
                                                saved = true
                                                Toast.makeText(context, "已保存「$word」→ 单词短语", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "请先创建英语题库", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onLongClick = { deleteMode = true; selectedForDelete = setOf(word) }
                                ),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = bgColor),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (saved) { Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                                    Text(word, fontSize = 12.sp, color = if (deleteMode && isSelected) Color(0xFFD32F2F) else Color(0xFF333333))
                                }
                            }
                        }
                    }
                }

                // 标记的长难句
                if (markedSentences.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("📖 长难句（点击加入题库，长按删除）", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1976D2))
                    Spacer(Modifier.height(6.dp))
                    markedSentences.forEachIndexed { idx, sentence ->
                        var saved by remember { mutableStateOf(false) }
                        val isSelected = sentence in selectedForDelete
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (deleteMode) {
                                            selectedForDelete = if (isSelected) selectedForDelete - sentence else selectedForDelete + sentence
                                        } else {
                                            val engBank = vm.questionBanks.find { it.subject == Subject.ENGLISH }
                                            if (engBank != null) {
                                                vm.addQuestion(engBank.id, sentence, "", QuestionType.LONG_SENTENCE, isMemorize = true)
                                                saved = true
                                                Toast.makeText(context, "已保存长难句 → 英语题库", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "请先创建英语题库", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onLongClick = { deleteMode = true; selectedForDelete = setOf(sentence) }
                                ),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    deleteMode && isSelected -> Color(0xFFFFCDD2)
                                    saved -> Color(0xFFE8F5E9)
                                    else -> Color(0xFFE3F2FD)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
                                Text("${idx + 1}.", fontSize = 12.sp, color = Color(0xFF1976D2), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Text(sentence, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
                                if (saved) Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 在文本中高亮用户标记的词汇 */
private fun buildHighlightedText(text: String, markedWords: List<String>): androidx.compose.ui.text.AnnotatedString {
    if (markedWords.isEmpty()) return androidx.compose.ui.text.buildAnnotatedString { append(text) }
    return androidx.compose.ui.text.buildAnnotatedString {
        var pos = 0
        val lowerText = text.lowercase()
        while (pos < text.length) {
            var matched = false
            for (kw in markedWords.sortedByDescending { it.length }) {
                val kwLower = kw.lowercase()
                if (lowerText.startsWith(kwLower, pos)) {
                    val before = if (pos > 0) text[pos - 1] else ' '
                    val after = if (pos + kw.length < text.length) text[pos + kw.length] else ' '
                    if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(
                            fontWeight = FontWeight.Bold, color = Color(0xFFE65100), background = Color(0xFFFFF9C4)
                        ))
                        append(text.substring(pos, pos + kw.length))
                        pop()
                        pos += kw.length
                        matched = true
                        break
                    }
                }
            }
            if (!matched) { append(text[pos]); pos++ }
        }
    }
}

// ==================== 简易 Markdown 渲染 ====================
@Composable
fun SimpleMarkdownText(text: String) {
    val lines = text.lines()
    lines.forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("#### ") -> {
                Spacer(Modifier.height(8.dp))
                Text(trimmed.removePrefix("#### ").trim(),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333),
                    lineHeight = 20.sp)
                Spacer(Modifier.height(4.dp))
            }
            trimmed.startsWith("### ") -> {
                Spacer(Modifier.height(12.dp))
                Text(trimmed.removePrefix("### ").trim(),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00),
                    lineHeight = 22.sp)
                Spacer(Modifier.height(6.dp))
            }
            trimmed.startsWith("## ") -> {
                Spacer(Modifier.height(14.dp))
                Text(trimmed.removePrefix("## ").trim(),
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100),
                    lineHeight = 24.sp)
                Spacer(Modifier.height(6.dp))
            }
            trimmed.startsWith("# ") -> {
                Spacer(Modifier.height(16.dp))
                Text(trimmed.removePrefix("# ").trim(),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBF360C),
                    lineHeight = 26.sp)
                Spacer(Modifier.height(8.dp))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val bullet = trimmed.removePrefix("- ").removePrefix("* ").trim()
                Row(modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp)) {
                    Text("•  ", fontSize = 14.sp, color = Color(0xFFFF8A00))
                    Text(text = renderInlineMarkdown(bullet), fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
            trimmed.isEmpty() -> Spacer(Modifier.height(4.dp))
            else -> {
                Text(text = renderInlineMarkdown(trimmed), fontSize = 14.sp, lineHeight = 22.sp,
                    modifier = Modifier.padding(vertical = 1.dp))
            }
        }
    }
}

/** 处理行内 **粗体** 标记，返回 AnnotatedString */
internal fun renderInlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return androidx.compose.ui.text.buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            val boldStart = remaining.indexOf("**")
            if (boldStart == -1) {
                append(remaining)
                break
            }
            append(remaining.substring(0, boldStart))
            remaining = remaining.substring(boldStart + 2)
            val boldEnd = remaining.indexOf("**")
            if (boldEnd == -1) {
                append("**$remaining")
                break
            }
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF333333)))
            append(remaining.substring(0, boldEnd))
            pop()
            remaining = remaining.substring(boldEnd + 2)
        }
    }
}

private fun loadSavedArticles(prefs: android.content.SharedPreferences): List<Map<String, String>> {
    val json = prefs.getString("saved_articles", "[]") ?: "[]"
    return try {
        com.google.gson.Gson().fromJson(json, object : com.google.gson.reflect.TypeToken<List<Map<String, String>>>() {}.type) ?: emptyList()
    } catch (_: Exception) { emptyList() }
}

private fun saveArticles(prefs: android.content.SharedPreferences, articles: List<Map<String, String>>) {
    prefs.edit().putString("saved_articles", com.google.gson.Gson().toJson(articles)).apply()
}
