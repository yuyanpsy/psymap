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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudySessionPage(vm: PsyMapViewModel, onFinish: () -> Unit) {
    val scope = rememberCoroutineScope()
    val question = vm.getCurrentQuestion()

    if (question == null) {
        SessionCompleteView(vm, onFinish)
        return
    }

    // 持久化每道题的作答状态（切换题目不丢失）
    var answeredMap by remember { mutableStateOf(mutableMapOf<String, Boolean>()) }  // questionId -> isCorrect
    var showAnswerMap by remember { mutableStateOf(mutableMapOf<String, Boolean>()) }
    var selectedOptionMap by remember { mutableStateOf(mutableMapOf<String, Int>()) }
    var selectedOptionsMap by remember { mutableStateOf(mutableMapOf<String, Set<Int>>()) }
    var userTextMap by remember { mutableStateOf(mutableMapOf<String, String>()) }

    val qId = question.id
    val alreadyStudiedToday = vm.isStudiedToday(qId) && !answeredMap.containsKey(qId)
    val isAnswered = answeredMap.containsKey(qId) || alreadyStudiedToday
    val isCorrect = answeredMap[qId] ?: false
    val showAnswer = showAnswerMap[qId] ?: alreadyStudiedToday
    val selectedOption = selectedOptionMap[qId] ?: -1
    val selectedOptions = selectedOptionsMap[qId] ?: emptySet()
    val userTextAnswer = userTextMap[qId] ?: ""
    val isMultiChoice = question.type == QuestionType.MULTI_CHOICE

    // 切题时重置AI评分状态（但不重置作答状态）
    LaunchedEffect(question.id) {
        if (!answeredMap.containsKey(question.id)) {
            vm.aiGradeScore = -1
            vm.aiGradeResult = ""
        }
    }

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
            if (alreadyStudiedToday) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("📌 今日已学习过此题", fontSize = 12.sp, color = Color(0xFFE65100),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            SimpleMarkdownText(question.content)

            Spacer(Modifier.height(20.dp))

            // 选择题选项
            if ((question.type == QuestionType.SINGLE_CHOICE || question.type == QuestionType.MULTI_CHOICE) && question.options.isNotEmpty()) {
                if (isMultiChoice && !isAnswered) {
                    Text("（多选题，可选择多个选项）", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(4.dp))
                }
                question.options.forEachIndexed { index, option ->
                    val optionLabel = ('A' + index).toString()
                    val isSelected = if (isMultiChoice) index in selectedOptions else selectedOption == index
                    // 解析正确答案字母集合（支持 "ABD"、"A,B,D"、"A B D" 等格式）
                    val correctLetters = question.answer.trim().uppercase()
                        .replace(Regex("[,，\\s]+"), "")
                        .toCharArray().map { it.toString() }.toSet()
                    val isCorrectOption = optionLabel in correctLetters
                    val bgColor = when {
                        !isAnswered -> if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.White
                        isCorrectOption -> Color(0xFFE8F5E9)
                        isSelected -> Color(0xFFFFEBEE)
                        else -> Color.White
                    }
                    val borderColor = when {
                        !isAnswered -> if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE0E0E0)
                        isCorrectOption -> Color(0xFF4CAF50)
                        isSelected -> Color(0xFFD32F2F)
                        else -> Color(0xFFE0E0E0)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                            .clickable(enabled = !isAnswered) {
                                if (isMultiChoice) {
                                    val cur = selectedOptionsMap[qId] ?: emptySet()
                                    selectedOptionsMap = selectedOptionsMap.toMutableMap().apply { put(qId, if (index in cur) cur - index else cur + index) }
                                } else {
                                    selectedOptionMap = selectedOptionMap.toMutableMap().apply { put(qId, index) }
                                }
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
                            Text(text = renderInlineMarkdown(option), fontSize = 14.sp)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                val canSubmit = if (isMultiChoice) selectedOptions.isNotEmpty() else selectedOption >= 0
                if (!isAnswered && canSubmit) {
                    Button(
                        onClick = {
                            var correct = false
                            if (isMultiChoice) {
                                val selectedLetters = selectedOptions.sorted().map { ('A' + it).toString() }.toSet()
                                val correctLetters = question.answer.trim().uppercase()
                                    .replace(Regex("[,，\\s]+"), "")
                                    .toCharArray().map { it.toString() }.toSet()
                                correct = selectedLetters == correctLetters
                                vm.submitAnswer(question.id, selectedLetters.sorted().joinToString(""), correct)
                            } else {
                                val selectedLetter = ('A' + selectedOption).toString()
                                val selectedText = question.options[selectedOption]
                                correct = question.answer.trim().equals(selectedLetter, ignoreCase = true)
                                    || question.answer.trim().equals(selectedText.trim(), ignoreCase = true)
                                    || selectedText.trim().startsWith(question.answer.trim(), ignoreCase = true)
                                    || question.answer.trim().startsWith(selectedLetter, ignoreCase = true)
                                vm.submitAnswer(question.id, selectedLetter, correct)
                            }
                            answeredMap = answeredMap.toMutableMap().apply { put(qId, correct) }
                            showAnswerMap = showAnswerMap.toMutableMap().apply { put(qId, true) }
                            // 答对自动跳下一题（延迟1秒让用户看到结果）
                            if (correct) {
                                scope.launch {
                                    kotlinx.coroutines.delay(1000)
                                    vm.moveToNext()
                                }
                            }
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
                        onValueChange = { userTextMap = userTextMap.toMutableMap().apply { put(qId, it) } },
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
                                showAnswerMap = showAnswerMap.toMutableMap().apply { put(qId, true) }
                                if (userTextAnswer.isNotBlank()) {
                                    vm.gradeSubjectiveAnswer(question, userTextAnswer)
                                    answeredMap = answeredMap.toMutableMap().apply { put(qId, vm.aiGradeScore >= 60) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("提交答案")
                        }
                        OutlinedButton(
                            onClick = {
                                showAnswerMap = showAnswerMap.toMutableMap().apply { put(qId, true) }
                            },
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
                // 使用实时的题目数据（答案可能被AI异步更新）
                val liveQuestion = vm.questions.find { it.id == question.id } ?: question
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
                        if (liveQuestion.answer.isNotBlank()) {
                            SimpleMarkdownText(liveQuestion.answer)
                        } else {
                            Text("该题暂无标准答案", fontSize = 13.sp, color = Color.Gray)
                        }
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
                if ((question.type == QuestionType.SINGLE_CHOICE || question.type == QuestionType.MULTI_CHOICE) && isAnswered) {
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

                // 解析（所有题型均可显示）
                if (liveQuestion.explanation.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("💡 解析", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                                color = Color(0xFF7B1FA2))
                            Spacer(Modifier.height(8.dp))
                            SimpleMarkdownText(liveQuestion.explanation)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // 上一题/下一题导航按钮
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
            Spacer(Modifier.height(32.dp))
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
