package com.psymap.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
        StudySessionPage(vm = vm, onFinish = { showStudySession = false })
    }

    // 单题作答弹窗（错题本/收藏本点击）
    singleQuestionToAnswer?.let { q ->
        SingleQuestionDialog(vm = vm, question = q, onDismiss = { singleQuestionToAnswer = null })
    }
}

// ==================== 单题作答弹窗（支持AI评分） ====================
@Composable
fun SingleQuestionDialog(vm: PsyMapViewModel, question: Question, onDismiss: () -> Unit) {
    var userAnswer by remember { mutableStateOf("") }
    var showAnswer by remember { mutableStateOf(false) }
    var selectedOption by remember { mutableStateOf(-1) }
    var answered by remember { mutableStateOf(false) }
    var isCorrect by remember { mutableStateOf(false) }
    var aiGrading by remember { mutableStateOf(false) }
    val isChoice = question.type == QuestionType.SINGLE_CHOICE || question.type == QuestionType.MULTI_CHOICE
    val isLoading by vm.isLoading.collectAsState()

    AlertDialog(
        onDismissRequest = { if (!aiGrading) onDismiss() },
        title = { Text(question.type.label, fontSize = 14.sp, color = Color.Gray) },
        text = {
            Column {
                Text(question.content, fontSize = 15.sp, lineHeight = 22.sp)
                Spacer(Modifier.height(12.dp))

                if (isChoice && question.options.isNotEmpty() && !answered) {
                    question.options.forEachIndexed { idx, opt ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { selectedOption = idx }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedOption == idx, onClick = { selectedOption = idx })
                            Spacer(Modifier.width(4.dp))
                            Text("${('A' + idx)}. $opt", fontSize = 14.sp)
                        }
                    }
                } else if (!isChoice && !showAnswer && !aiGrading) {
                    OutlinedTextField(
                        value = userAnswer, onValueChange = { userAnswer = it },
                        label = { Text("你的答案") }, modifier = Modifier.fillMaxWidth(), maxLines = 5
                    )
                }

                // AI评分中
                if (aiGrading) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("AI 正在评分...", fontSize = 13.sp, color = Color.Gray)
                    }
                }

                // AI评分结果
                if (vm.aiGradeScore >= 0 && answered && !isChoice) {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("🤖 AI评分: ${vm.aiGradeScore}分",
                                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = if (vm.aiGradeScore >= 60) Color(0xFF4CAF50) else Color(0xFFD32F2F))
                            if (vm.aiGradeResult.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(vm.aiGradeResult, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }

                if (showAnswer || answered) {
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("参考答案", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFEF6C00))
                            Spacer(Modifier.height(4.dp))
                            Text(question.answer, fontSize = 14.sp)
                        }
                    }
                    if (answered && isChoice) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isCorrect) "✅ 回答正确" else "❌ 回答错误",
                            color = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFD32F2F),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!answered && !showAnswer && !aiGrading) {
                Button(onClick = {
                    if (isChoice && selectedOption >= 0) {
                        answered = true
                        showAnswer = true
                        val selectedLetter = ('A' + selectedOption).toString()
                        isCorrect = question.answer.trim().equals(selectedLetter, ignoreCase = true)
                            || question.answer.trim().equals(question.options[selectedOption].trim(), ignoreCase = true)
                        vm.submitAnswer(question.id, selectedLetter, isCorrect)
                        if (isCorrect && question.isInWrongBook) vm.removeFromWrongBook(question.id)
                    } else if (!isChoice && userAnswer.isNotBlank()) {
                        // 主观题：调用AI评分
                        aiGrading = true
                        vm.gradeSubjectiveAnswer(question, userAnswer)
                    }
                }, enabled = if (isChoice) selectedOption >= 0 else userAnswer.isNotBlank()) {
                    Text("提交")
                }
            } else if (aiGrading && vm.aiGradeScore < 0) {
                // 等待AI评分中，不显示按钮
            } else {
                // AI评分完成或选择题已答
                if (aiGrading && vm.aiGradeScore >= 0 && !answered) {
                    // AI评分返回了，更新状态
                    LaunchedEffect(vm.aiGradeScore) {
                        answered = true
                        showAnswer = true
                        isCorrect = vm.aiGradeScore >= 60
                        aiGrading = false
                    }
                }
                Button(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {
            if (!answered && !showAnswer && !aiGrading) {
                TextButton(onClick = { showAnswer = true }) { Text("查看答案") }
            }
        }
    )
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
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(bank.subject.emoji, fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(bank.name, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text("${questions.size} 题", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                    if (questions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        val correct = questions.sumOf { it.correctCount }
                        val total = questions.sumOf { it.correctCount + it.wrongCount }
                        val progress = if (total > 0) correct.toFloat() / total else 0f
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.weight(1f).height(6.dp),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color(0xFFE0E0E0)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = Color.Gray)
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
        item { Text("共 ${wrongQuestions.size} 道错题  ·  点击可作答", fontSize = 13.sp, color = Color.Gray) }
        items(wrongQuestions) { question ->
            val bank = vm.questionBanks.find { it.id == question.bankId }
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
        item { Text("点击题目可直接作答", fontSize = 13.sp, color = Color.Gray) }
        items(favorites) { question ->
            val bank = vm.questionBanks.find { it.id == question.bankId }
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

