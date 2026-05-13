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
                QuestionDetailDialog(
                    question = currentQuestion,
                    vm = vm,
                    questionList = currentScreen.questionList,
                    onDismiss = { screen = PracticeScreen.Main },
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
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(bank.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                                Spacer(Modifier.width(6.dp))
                                Text("共 ${questions.size} 题", fontSize = 12.sp, color = Color.Gray)
                            }
                            val todayUniqueCount = vm.todayCheckIn.bankStudiedIds[bank.id]?.size ?: 0
                            val todayCorrectCount = vm.todayCheckIn.bankCorrectIds[bank.id]?.size ?: 0
                            val correctRateText = if (todayUniqueCount > 0) "，正确率 ${(todayCorrectCount * 100 / todayUniqueCount)}%" else ""
                            Text("今日已学习 $todayUniqueCount 题$correctRateText", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    if (questions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            Text("乱序学习", fontSize = 13.sp, color = Color(0xFFFF8A00), fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { onStartStudy(bank.id, true) })
                            Spacer(Modifier.width(16.dp))
                            Text("|", fontSize = 13.sp, color = Color(0xFFE0E0E0))
                            Spacer(Modifier.width(16.dp))
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



