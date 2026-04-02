package com.psymap.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudySessionPage(vm: PsyMapViewModel, onFinish: () -> Unit) {
    val question = vm.getCurrentQuestion()

    if (question == null) {
        // 学习完成
        SessionCompleteView(vm, onFinish)
        return
    }

    var showAnswer by remember(question.id) { mutableStateOf(false) }
    var selectedOption by remember(question.id) { mutableStateOf(-1) }
    var userTextAnswer by remember(question.id) { mutableStateOf("") }
    var answered by remember(question.id) { mutableStateOf(false) }
    var isCorrect by remember(question.id) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("${vm.currentQuestionIndex + 1} / ${vm.getStudySessionSize()}",
                        fontSize = 16.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Default.Close, contentDescription = "退出")
                    }
                },
                actions = {
                    // 收藏按钮
                    IconButton(onClick = { vm.toggleFavorite(question.id) }) {
                        Icon(
                            if (question.isInFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "收藏",
                            tint = if (question.isInFavorites) Color(0xFFFF9800) else Color.Gray
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
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
            // 题目类型标签
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(
                    onClick = {},
                    label = { Text(question.type.label, fontSize = 11.sp) }
                )
                if (question.isFrequent) {
                    AssistChip(onClick = {}, label = { Text("🔥常考", fontSize = 11.sp) })
                }
                if (question.isMemorize) {
                    AssistChip(onClick = {}, label = { Text("📖多背", fontSize = 11.sp) })
                }
                if (question.chapter.isNotBlank()) {
                    AssistChip(
                        onClick = {},
                        label = { Text(question.chapter, fontSize = 11.sp) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 题目内容
            Text(question.content, fontSize = 16.sp, lineHeight = 24.sp)

            Spacer(Modifier.height(20.dp))

            // 选择题选项
            if ((question.type == QuestionType.SINGLE_CHOICE || question.type == QuestionType.MULTI_CHOICE) && question.options.isNotEmpty()) {
                question.options.forEachIndexed { index, option ->
                    val optionLabel = ('A' + index).toString()
                    val isSelected = selectedOption == index
                    val isCorrectOption = question.answer.trim().equals(optionLabel, ignoreCase = true)
                        || question.answer.trim().equals(option.trim(), ignoreCase = true)
                    val bgColor = when {
                        !answered -> if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White
                        isCorrectOption -> Color(0xFFE8F5E9)
                        isSelected -> Color(0xFFFFEBEE)
                        else -> Color.White
                    }
                    val borderColor = when {
                        !answered -> if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0)
                        isCorrectOption -> Color(0xFF4CAF50)
                        isSelected -> Color(0xFFD32F2F)
                        else -> Color(0xFFE0E0E0)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .clickable(enabled = !answered) {
                                selectedOption = index
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = bgColor),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("$optionLabel.", fontWeight = FontWeight.Medium,
                                color = borderColor, fontSize = 15.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(option, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!answered && selectedOption >= 0) {
                    Button(
                        onClick = {
                            answered = true
                            val selectedLetter = ('A' + selectedOption).toString()
                            val selectedText = question.options[selectedOption]
                            // 支持多种答案格式：字母("A")、完整选项文本、或选项包含答案
                            isCorrect = question.answer.trim().equals(selectedLetter, ignoreCase = true)
                                || question.answer.trim().equals(selectedText.trim(), ignoreCase = true)
                                || selectedText.trim().startsWith(question.answer.trim(), ignoreCase = true)
                                || question.answer.trim().startsWith(selectedLetter, ignoreCase = true)
                            vm.submitAnswer(question.id, selectedLetter, isCorrect)
                            showAnswer = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("确认答案")
                    }
                }
            }

            // 主观题
            if (question.type != QuestionType.SINGLE_CHOICE && question.type != QuestionType.MULTI_CHOICE) {
                if (!showAnswer) {
                    OutlinedTextField(
                        value = userTextAnswer,
                        onValueChange = { userTextAnswer = it },
                        label = { Text("输入你的答案") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        maxLines = 10
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                showAnswer = true
                                if (userTextAnswer.isNotBlank()) {
                                    vm.gradeSubjectiveAnswer(question, userTextAnswer)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("提交答案")
                        }
                        OutlinedButton(
                            onClick = { showAnswer = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("查看答案")
                        }
                    }
                }
            }

            // 显示答案
            if (showAnswer) {
                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📝 参考答案", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = Color(0xFFEF6C00))
                        Spacer(Modifier.height(8.dp))
                        Text(question.answer, fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }

                // AI评分结果
                if (vm.aiGradeScore >= 0) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🤖 AI评分: ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${vm.aiGradeScore}分",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (vm.aiGradeScore >= 60) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                                )
                            }
                            if (vm.aiGradeResult.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(vm.aiGradeResult, fontSize = 13.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }

                // 答题结果（选择题）
                if ((question.type == QuestionType.SINGLE_CHOICE || question.type == QuestionType.MULTI_CHOICE) && answered) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFD32F2F)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isCorrect) "回答正确！" else "回答错误，已加入错题本",
                                fontWeight = FontWeight.Medium,
                                color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 上一题/下一题按钮
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vm.currentQuestionIndex > 0) {
                        OutlinedButton(
                            onClick = { vm.moveToPrev() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("上一题")
                        }
                    }
                    Button(
                        onClick = {
                            if (!vm.moveToNext()) {
                                onFinish()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (vm.currentQuestionIndex < vm.getStudySessionSize() - 1) "下一题" else "完成")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SessionCompleteView(vm: PsyMapViewModel, onFinish: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = Color(0xFFFF9800)
            )
            Spacer(Modifier.height(16.dp))
            Text("学习完成！", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("本次学习", fontSize = 14.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${vm.sessionTotalCount}", fontSize = 28.sp,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("总题数", fontSize = 12.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${vm.sessionCorrectCount}", fontSize = 28.sp,
                                fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            Text("正确", fontSize = 12.sp, color = Color.Gray)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(vm.sessionAccuracy * 100).toInt()}%", fontSize = 28.sp,
                                fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                            Text("正确率", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    vm.recordCheckIn(vm.sessionTotalCount)
                    onFinish()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("完成打卡", fontSize = 16.sp)
            }
        }
    }
}
