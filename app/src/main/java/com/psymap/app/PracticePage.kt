package com.psymap.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.window.Dialog
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

@Composable
fun PracticePage(vm: PsyMapViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("题库学习", "错题本", "收藏本")

    var showStudySession by remember { mutableStateOf(false) }
    var singleQuestionToAnswer by remember { mutableStateOf<Question?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
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
            0 -> BankPracticeList(vm) { bankId, shuffle ->
                vm.startStudySession(bankId, shuffle)
                showStudySession = true
            }
            1 -> WrongBookList(vm) { question ->
                singleQuestionToAnswer = question
            }
            2 -> FavoritesList(vm) { question ->
                singleQuestionToAnswer = question
            }
        }
    }

    if (showStudySession) {
        Dialog(
            onDismissRequest = { showStudySession = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            StudySessionPage(vm = vm, onFinish = { showStudySession = false })
        }
    }

    // 单题作答 — 使用全屏 QuestionDetailDialog
    singleQuestionToAnswer?.let { q ->
        QuestionDetailDialog(question = q, vm = vm, onDismiss = { singleQuestionToAnswer = null })
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
        items(vm.visibleBanks) { bank ->
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
                                modifier = Modifier.weight(1f).height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color(0xFFE0E0E0)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (todayUniqueStudied > 0) "${(progress * 100).toInt()}%" else "--",
                                fontSize = 12.sp, color = Color.Gray,
                                modifier = Modifier.width(46.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1)
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onStartStudy(bank.id, false) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                                Text("顺序学习", fontSize = 13.sp)
                            }
                            OutlinedButton(onClick = { onStartStudy(bank.id, true) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                                Text("乱序学习", fontSize = 13.sp)
                            }
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
            val bank = vm.visibleBanks.find { it.id == question.bankId }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onClickQuestion(question) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (bank != null) {
                            Text("${bank.subject.emoji} ${bank.name}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("错误率 ${(question.errorRate * 100).toInt()}%", fontSize = 11.sp, color = Color(0xFFD32F2F))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(question.content, fontSize = 14.sp, maxLines = 3)
                    Spacer(Modifier.height(4.dp))
                    Text("已复习 ${question.reviewCount} 次", fontSize = 11.sp, color = Color.Gray)
                }
            }
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
                Text("暂无收藏题目", color = Color.Gray)
            }
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("点击可编辑", fontSize = 13.sp, color = Color.Gray) }
        items(favorites) { question ->
            val bank = vm.visibleBanks.find { it.id == question.bankId }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onClickQuestion(question) },
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(question.content, fontSize = 14.sp, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Row {
                            if (bank != null) Text("${bank.subject.emoji} ${bank.name}", fontSize = 11.sp, color = Color.Gray)
                            Spacer(Modifier.width(8.dp))
                            Text("错误率 ${(question.errorRate * 100).toInt()}%", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                    IconButton(onClick = { vm.toggleFavorite(question.id) }) {
                        Icon(Icons.Default.Star, contentDescription = "取消收藏", tint = Color(0xFFFF9800))
                    }
                }
            }
        }
    }
}

