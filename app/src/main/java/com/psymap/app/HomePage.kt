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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                shape = RoundedCornerShape(24.dp),
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
                onPhotoSearch = { cameraPermission.launch(android.Manifest.permission.CAMERA) },
                onStudyPlan = { showStudyPlan = true },
                onWrongBook = { showWrongBookDialog = true },
                onFavorites = { showFavoritesDialog = true },
                onMyBanks = { searchText = ""; vm.searchQuestions("") },
                onMakeAudio = { showMakeAudio = true },
                onCalendar = { showCalendar = true },
                onListen = { showListenAudio = true }
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
    onPhotoSearch: () -> Unit, onStudyPlan: () -> Unit,
    onWrongBook: () -> Unit, onFavorites: () -> Unit,
    onMyBanks: () -> Unit, onMakeAudio: () -> Unit,
    onCalendar: () -> Unit, onListen: () -> Unit
) {
    val actions = listOf(
        Triple(Icons.Default.PhotoCamera, "拍照搜题", onPhotoSearch),
        Triple(Icons.Default.Schedule, "学习计划", onStudyPlan),
        Triple(Icons.Default.ErrorOutline, "复习错题", onWrongBook),
        Triple(Icons.Default.Star, "收藏题目", onFavorites),
        Triple(Icons.Default.LibraryBooks, "我的题库", onMyBanks),
        Triple(Icons.Default.CalendarMonth, "打卡日历", onCalendar),
        Triple(Icons.Default.RecordVoiceOver, "制作音频", onMakeAudio),
        Triple(Icons.Default.Headphones, "磨耳朵", onListen)
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        for (row in actions.chunked(4)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for ((icon, label, onClick) in row) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 8.dp).width(72.dp)) {
                        Box(modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center, color = Color(0xFF333333))
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
        shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(bank.subject.emoji, fontSize = 28.sp)
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
    var selectedSubject by remember { mutableStateOf(Subject.GENERAL_PSY) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建题库") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("题库名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text("选择科目", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Subject.entries.forEach { subject ->
                        FilterChip(selected = selectedSubject == subject, onClick = { selectedSubject = subject },
                            label = { Text("${subject.emoji}${subject.label}", fontSize = 12.sp) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("可用题型: ${selectedSubject.availableQuestionTypes().joinToString(" · ") { it.label }}", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = { Button(onClick = { vm.createBank(name.ifBlank { selectedSubject.label }, selectedSubject); onDismiss() }) { Text("创建") } },
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
                        Spacer(Modifier.width(8.dp))
                        Text("${(rate * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(36.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ==================== 打卡日历弹窗（支持切换月份） ====================
@Composable
fun CheckInCalendarDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var displayMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var displayYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    val todayCal = Calendar.getInstance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (displayMonth == 0) { displayMonth = 11; displayYear-- } else displayMonth--
                }) { Icon(Icons.Default.ChevronLeft, contentDescription = "上月") }
                Text("${displayYear}年${displayMonth + 1}月", fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    if (displayMonth == 11) { displayMonth = 0; displayYear++ } else displayMonth++
                }) { Icon(Icons.Default.ChevronRight, contentDescription = "下月") }
            }
        },
        text = {
            Column {
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ==================== 全部错题弹窗（按题库分组） ====================
@Composable
fun WrongBookDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val wrongQuestions = vm.getWrongQuestions()
    val grouped = wrongQuestions.groupBy { it.bankId }
    var showStudySession by remember { mutableStateOf(false) }

    if (showStudySession) {
        StudySessionPage(vm = vm, onFinish = { showStudySession = false })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("全部错题 (${wrongQuestions.size})") },
        text = {
            if (wrongQuestions.isEmpty()) {
                Text("暂无错题，继续保持！", color = Color.Gray)
            } else {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    grouped.forEach { (bankId, questions) ->
                        val bank = vm.questionBanks.find { it.id == bankId }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable {
                                vm.startStudySessionWithQuestions(questions.map { it.id })
                                showStudySession = true
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${bank?.subject?.emoji ?: "📋"} ${bank?.name ?: "未知题库"}",
                                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text("${questions.size} 题 →", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                questions.take(3).forEach { q ->
                                    Text("· ${q.content.take(30)}...", fontSize = 12.sp, color = Color.Gray,
                                        maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                                }
                                if (questions.size > 3) Text("...还有 ${questions.size - 3} 题", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ==================== 收藏题目弹窗（按题库分组） ====================
@Composable
fun FavoritesDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val favorites = vm.getFavoriteQuestions()
    val grouped = favorites.groupBy { it.bankId }
    var showStudySession by remember { mutableStateOf(false) }

    if (showStudySession) {
        StudySessionPage(vm = vm, onFinish = { showStudySession = false })
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("收藏题目 (${favorites.size})") },
        text = {
            if (favorites.isEmpty()) {
                Text("暂无收藏题目", color = Color.Gray)
            } else {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    grouped.forEach { (bankId, questions) ->
                        val bank = vm.questionBanks.find { it.id == bankId }
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .clickable {
                                vm.startStudySessionWithQuestions(questions.map { it.id })
                                showStudySession = true
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${bank?.subject?.emoji ?: "📋"} ${bank?.name ?: "未知题库"}",
                                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text("${questions.size} 题 →", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                questions.take(3).forEach { q ->
                                    Text("· ${q.content.take(30)}...", fontSize = 12.sp, color = Color.Gray,
                                        maxLines = 1, modifier = Modifier.padding(top = 2.dp))
                                }
                                if (questions.size > 3) Text("...还有 ${questions.size - 3} 题", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

// ==================== 文件导入对话框 ====================
@OptIn(ExperimentalLayoutApi::class)
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

    // 监听导入结果
    LaunchedEffect(vm.importResultMessage) {
        if (vm.importResultMessage.isNotBlank()) {
            Toast.makeText(context, vm.importResultMessage, Toast.LENGTH_LONG).show()
            if (vm.importResultMessage.startsWith("成功")) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("导入文件到题库") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
            }
        },
        confirmButton = {
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
                            // 图片文件：直接 OCR
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
                            // PDF: 渲染页面为图片再 OCR
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
                            // 文本文件
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
                enabled = !isLoading && (selectedBankId.isNotBlank() || (isCreatingNew && newBankName.isNotBlank()))
            ) { Text("开始导入") }
        },
        dismissButton = {
            if (!isLoading) TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ==================== 学习计划弹窗 ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudyPlanDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var showEdit by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("每日学习计划")
                Button(onClick = { showEdit = true }, shape = RoundedCornerShape(8.dp)) {
                    Text("编辑", fontSize = 13.sp)
                }
            }
        },
        text = {
            Column {
                vm.questionBanks.forEach { bank ->
                    val target = vm.dailyTargets[bank.id] ?: 0
                    val done = vm.todayCheckIn.bankProgress[bank.id] ?: 0
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${bank.subject.emoji} ${bank.name}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (target > 0) {
                            Text("$done/$target", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (done >= target) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary)
                        } else {
                            Text("未设定", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                    if (target > 0) {
                        LinearProgressIndicator(
                            progress = { (done.toFloat() / target).coerceAtMost(1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = if (done >= target) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                            trackColor = Color(0xFFE0E0E0)
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )

    if (showEdit) {
        EditDailyTargetsDialog(vm = vm, onDismiss = { showEdit = false })
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

// ==================== 制作音频弹窗 ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MakeAudioDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    var selectedBankId by remember { mutableStateOf(vm.questionBanks.firstOrNull()?.id ?: "") }
    var questionCount by remember { mutableStateOf("10") }
    var shuffle by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isGenerating) onDismiss() },
        title = { Text("制作音频") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("选择题库", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                vm.questionBanks.forEach { bank ->
                    val count = vm.getQuestionsForBank(bank.id).size
                    Row(modifier = Modifier.fillMaxWidth().clickable { selectedBankId = bank.id }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedBankId == bank.id, onClick = { selectedBankId = bank.id })
                        Spacer(Modifier.width(4.dp))
                        Text("${bank.subject.emoji} ${bank.name} (${count}题)", fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = questionCount, onValueChange = { questionCount = it.filter { c -> c.isDigit() } },
                    label = { Text("题目数量") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    suffix = { Text("题") })
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = shuffle, onCheckedChange = { shuffle = it })
                    Text("乱序", fontSize = 14.sp)
                }
                if (isGenerating) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(progress, fontSize = 12.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isGenerating = true
                    progress = "初始化语音引擎..."
                    val bankQuestions = vm.getQuestionsForBank(selectedBankId)
                    val cnt = (questionCount.toIntOrNull() ?: 10).coerceAtMost(bankQuestions.size)
                    val selected = if (shuffle) bankQuestions.shuffled().take(cnt) else bankQuestions.take(cnt)
                    val bank = vm.questionBanks.find { it.id == selectedBankId }
                    val bankName = bank?.name ?: "题库"

                    scope.launch(Dispatchers.Main) {
                        // TTS 初始化
                        val ttsDeferred = CompletableDeferred<android.speech.tts.TextToSpeech?>()
                        val tts = android.speech.tts.TextToSpeech(context) { status ->
                            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                                ttsDeferred.complete(android.speech.tts.TextToSpeech(context, null))
                            } else {
                                ttsDeferred.complete(null)
                            }
                        }
                        // 不用 delay 轮询，直接 await deferred（回调会在主线程执行）
                        val ttsEngine = withTimeoutOrNull(8000) { ttsDeferred.await() } ?: tts.also {
                            // 超时了，直接用第一个实例试试
                            null
                        }

                        // 简化：直接用 tts 实例，不管回调
                        tts.language = java.util.Locale.CHINESE
                        tts.setSpeechRate(0.9f)

                        val fullText = StringBuilder()
                        selected.forEachIndexed { idx, q ->
                            fullText.append("第${idx + 1}题。${q.content}。答案：${q.answer}。")
                        }

                        if (fullText.isEmpty()) {
                            isGenerating = false; progress = ""
                            Toast.makeText(context, "没有题目内容", Toast.LENGTH_SHORT).show()
                            tts.shutdown()
                            return@launch
                        }

                        val dir = java.io.File(context.getExternalFilesDir(null), "audio")
                        dir.mkdirs()
                        val fileName = "${bankName}_${cnt}q.wav"
                        val file = java.io.File(dir, fileName)

                        progress = "正在生成音频..."

                        val done = CompletableDeferred<Boolean>()
                        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) { }
                            override fun onDone(utteranceId: String?) { done.complete(true) }
                            @Deprecated("Deprecated") override fun onError(utteranceId: String?) { done.complete(false) }
                        })

                        // 先尝试写文件
                        var useSpeak = false
                        val writeResult = tts.synthesizeToFile(fullText.toString(), null, file, "psymap_tts")
                        if (writeResult != android.speech.tts.TextToSpeech.SUCCESS) {
                            useSpeak = true
                            progress = "正在朗读..."
                            tts.speak(fullText.toString(), android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "psymap_speak")
                        }

                        // 等待完成：用超时而不是依赖回调（小米TTS回调不可靠）
                        val estimatedMs = (fullText.length * 200L).coerceIn(5000, 120000) // 每字约200ms
                        val result = withTimeoutOrNull(estimatedMs) { done.await() }
                        if (result == null) {
                            // 超时了，但朗读可能还在进行，等额外2秒后强制结束
                            delay(2000)
                        }

                        tts.stop()
                        tts.shutdown()
                        isGenerating = false; progress = ""

                        val saved = file.exists() && file.length() > 100
                        Toast.makeText(context,
                            if (saved) "音频生成完成！" else if (useSpeak) "朗读完成" else "完成",
                            Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
                },
                enabled = !isGenerating && selectedBankId.isNotBlank()
            ) { Text(if (isGenerating) "生成中..." else "开始制作") }
        },
        dismissButton = { if (!isGenerating) TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ==================== 磨耳朵（播放音频）弹窗 ====================
@Composable
fun ListenAudioDialog(vm: PsyMapViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val audioDir = remember { java.io.File(context.getExternalFilesDir(null), "audio").apply { mkdirs() } }
    var audioFiles by remember { mutableStateOf(audioDir.listFiles()?.filter { it.extension in listOf("wav", "mp3", "ogg") }?.sortedBy { it.name }?.toList() ?: emptyList()) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaying by remember { mutableStateOf("") }
    var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

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
        // 刷新文件列表
        audioFiles = audioDir.listFiles()?.filter { it.extension in listOf("wav", "mp3", "ogg") }?.sortedBy { it.name }?.toList() ?: emptyList()
    }

    DisposableEffect(Unit) {
        onDispose { mediaPlayer?.release() }
    }

    AlertDialog(
        onDismissRequest = { mediaPlayer?.release(); onDismiss() },
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("磨耳朵")
                TextButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) {
                    Text("+ 导入音频", fontSize = 13.sp)
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (audioFiles.isEmpty()) {
                    Text("暂无音频文件\n• 使用「制作音频」生成题目音频\n• 或点击右上角「导入音频」选择本地文件", color = Color.Gray, fontSize = 14.sp)
                } else {
                    Text("选择要播放的音频文件 (${audioFiles.size}个)", fontSize = 13.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    audioFiles.forEach { file ->
                        Row(modifier = Modifier.fillMaxWidth().clickable {
                            selectedFiles = if (file.name in selectedFiles) selectedFiles - file.name else selectedFiles + file.name
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = file.name in selectedFiles,
                                onCheckedChange = { selectedFiles = if (it) selectedFiles + file.name else selectedFiles - file.name })
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text(file.nameWithoutExtension, fontSize = 14.sp)
                                Text("${file.length() / 1024} KB", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                    if (isPlaying) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("正在播放: $currentPlaying", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (audioFiles.isNotEmpty()) {
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
                    enabled = selectedFiles.isNotEmpty() || isPlaying
                ) { Text(if (isPlaying) "⏹ 停止" else "▶ 播放") }
            }
        },
        dismissButton = { TextButton(onClick = { mediaPlayer?.release(); onDismiss() }) { Text("关闭") } }
    )
}
