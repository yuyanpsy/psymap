package com.psymap.app

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 页面状态
private sealed class PracticeScreen {
    object Main : PracticeScreen()
    object StudySession : PracticeScreen()
    data class QuestionDetail(val question: Question, val questionList: kotlin.collections.List<Question>) : PracticeScreen()
}

@Composable
fun PracticePage(vm: PsyMapViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("题库学习", "错题本", "收藏本")
    var screen by remember { mutableStateOf<PracticeScreen>(PracticeScreen.Main) }

    Crossfade(
        targetState = screen,
        animationSpec = tween(200),
        label = "practice_nav"
    ) { currentScreen ->
        when (currentScreen) {
            is PracticeScreen.Main -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.White,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                                text = { Text(title, fontSize = 14.sp) })
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (selectedTab) {
                            0 -> BankPracticeList(vm) { bankId, shuffle ->
                                vm.startStudySession(bankId, shuffle)
                                screen = PracticeScreen.StudySession
                            }
                            1 -> WrongBookList(vm) { question ->
                                screen = PracticeScreen.QuestionDetail(question, vm.getWrongQuestions())
                            }
                            2 -> FavoritesList(vm) { question ->
                                screen = PracticeScreen.QuestionDetail(question, vm.getFavoriteQuestions())
                            }
                        }
                    }
                }
            }
            is PracticeScreen.StudySession -> {
                StudySessionPage(vm = vm, onFinish = { screen = PracticeScreen.Main })
            }
            is PracticeScreen.QuestionDetail -> {
                var currentQuestion by remember(currentScreen) { mutableStateOf(currentScreen.question) }
                QuestionDetailInline(
                    question = currentQuestion, vm = vm,
                    questionList = currentScreen.questionList,
                    onBack = { screen = PracticeScreen.Main },
                    onNavigate = { next -> currentQuestion = next }
                )
            }
        }
    }
}

// ==================== 题库练习列表 ====================
@Composable
fun BankPracticeList(vm: PsyMapViewModel, onStartStudy: (String, Boolean) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(vm.questionBanks) { bank ->
            val questions = vm.getQuestionsForBank(bank.id)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(bank.subject.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bank.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            val todayStudied = vm.todayCheckIn.bankProgress[bank.id] ?: 0
                            val todayUniqueCount = vm.todayCheckIn.bankStudiedIds[bank.id]?.size ?: 0
                            Text("共 ${questions.size} 题，今日已学习 $todayUniqueCount 题", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    if (questions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        val todayUniqueStudied = vm.todayCheckIn.bankStudiedIds[bank.id]?.size ?: 0
                        val todayCorrectCount = vm.todayCheckIn.bankCorrectIds[bank.id]?.size ?: 0
                        val progress = if (todayUniqueStudied > 0) todayCorrectCount.toFloat() / todayUniqueStudied else 0f
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color(0xFFE0E0E0),
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (todayUniqueStudied > 0) "${(progress * 100).toInt()}%" else "--",
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.width(46.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text("乱序学习", fontSize = 13.sp, color = Color(0xFFFF8A00), fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { onStartStudy(bank.id, true) })
                            Text("|", fontSize = 13.sp, color = Color(0xFFE0E0E0))
                            Text("顺序学习", fontSize = 13.sp, color = Color(0xFF1976D2),
                                modifier = Modifier.clickable { onStartStudy(bank.id, false) })
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Text("暂无题目", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// ==================== 错题本（可点击作答） ====================
@Composable
fun WrongBookList(vm: PsyMapViewModel, onClickQuestion: (Question) -> Unit) {
    val wrongQuestions = vm.getWrongQuestions()
    if (wrongQuestions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF4CAF50))
                Spacer(Modifier.height(8.dp))
                Text("暂无错题，继续保持！", color = Color.Gray)
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("共 ${wrongQuestions.size} 道错题  ·  点击可编辑", fontSize = 13.sp, color = Color.Gray) }
        items(wrongQuestions) { question ->
            QuestionListItem(question, vm, onClickQuestion)
        }
    }
}

// ==================== 收藏本（可点击作答） ====================
@Composable
fun FavoritesList(vm: PsyMapViewModel, onClickQuestion: (Question) -> Unit) {
    val favorites = vm.getFavoriteQuestions()
    if (favorites.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Text("暂无收藏", color = Color.Gray)
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("共 ${favorites.size} 道收藏  ·  点击可编辑", fontSize = 13.sp, color = Color.Gray) }
        items(favorites) { question ->
            QuestionListItem(question, vm, onClickQuestion)
        }
    }
}

// ==================== 统一题目卡片 ====================
@Composable
private fun QuestionListItem(question: Question, vm: PsyMapViewModel, onClick: (Question) -> Unit) {
    val bank = vm.questionBanks.find { it.id == question.bankId }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick(question) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(question.content, fontSize = 14.sp, maxLines = 3, lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (bank != null) {
                    Text("${bank.subject.emoji} ${bank.name}", fontSize = 11.sp, color = Color(0xFF888888))
                    Spacer(Modifier.width(12.dp))
                }
                Text("错误率 ${(question.errorRate * 100).toInt()}%", fontSize = 11.sp,
                    color = if (question.errorRate > 0.5) Color(0xFFD32F2F) else Color(0xFF888888))
                Spacer(Modifier.width(12.dp))
                Text("复习${question.reviewCount}次", fontSize = 11.sp, color = Color(0xFF888888))
            }
        }
    }
}



// ==================== 内联题目详情（不用 Dialog，直接替换页面内容） ====================
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuestionDetailInline(
    question: Question, vm: PsyMapViewModel,
    questionList: List<Question> = emptyList(),
    onBack: () -> Unit,
    onNavigate: ((Question) -> Unit)? = null
) {
    val liveQuestion = vm.questions.find { it.id == question.id } ?: question
    val currentIndex = if (questionList.isNotEmpty()) questionList.indexOfFirst { it.id == question.id } else -1
    val bank = vm.questionBanks.find { it.id == question.bankId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (questionList.size > 1) "题目详情 ${currentIndex + 1}/${questionList.size}" else "题目详情",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White, titleContentColor = Color(0xFFEF6C00), navigationIconContentColor = Color(0xFF333333), actionIconContentColor = Color(0xFF333333))
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
            // 题型 + 标签
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = {}, label = { Text(liveQuestion.type.label, fontSize = 11.sp) })
                if (liveQuestion.isFrequent) AssistChip(onClick = {}, label = { Text("🔥常考", fontSize = 11.sp) })
                if (liveQuestion.isMemorize) AssistChip(onClick = {}, label = { Text("📖多背", fontSize = 11.sp) })
                if (liveQuestion.isInFavorites) AssistChip(onClick = {}, label = { Text("⭐收藏", fontSize = 11.sp) })
                if (liveQuestion.isInWrongBook) AssistChip(onClick = {}, label = { Text("📌错题", fontSize = 11.sp) })
            }
            if (bank != null) {
                Spacer(Modifier.height(4.dp))
                Text("所属: ${bank.subject.emoji} ${bank.name}", fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))

            // 题目
            Text("题目", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            SimpleMarkdownText(liveQuestion.content)
            Spacer(Modifier.height(12.dp))

            // 选项
            if (liveQuestion.options.isNotEmpty()) {
                Text("选项", fontSize = 12.sp, color = Color.Gray)
                liveQuestion.options.forEachIndexed { i, opt ->
                    SimpleMarkdownText("${('A' + i)}. $opt")
                }
                Spacer(Modifier.height(12.dp))
            }

            // 答案
            Text("答案", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (liveQuestion.answer.isNotBlank()) SimpleMarkdownText(liveQuestion.answer)
                    else Text("暂无答案", color = Color.Gray, fontSize = 13.sp)
                }
            }

            // 解析
            if (liveQuestion.explanation.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("💡 解析", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE65100))
                        Spacer(Modifier.height(4.dp))
                        SimpleMarkdownText(liveQuestion.explanation)
                    }
                }
            }

            // 统计
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("复习 ${liveQuestion.reviewCount} 次", fontSize = 11.sp, color = Color.Gray)
                Text("正确 ${liveQuestion.correctCount}", fontSize = 11.sp, color = Color(0xFF4CAF50))
                Text("错误 ${liveQuestion.wrongCount}", fontSize = 11.sp, color = Color(0xFFD32F2F))
            }

            // 导航按钮
            if (questionList.size > 1 && onNavigate != null) {
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { if (currentIndex > 0) onNavigate(questionList[currentIndex - 1]) },
                        enabled = currentIndex > 0, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)
                    ) { Text("← 上一题") }
                    Text("${currentIndex + 1}/${questionList.size}", fontSize = 13.sp, color = Color.Gray)
                    Button(
                        onClick = { if (currentIndex < questionList.size - 1) onNavigate(questionList[currentIndex + 1]) },
                        enabled = currentIndex < questionList.size - 1, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)
                    ) { Text("下一题 →") }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
