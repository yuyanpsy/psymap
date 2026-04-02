package com.psymap.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverPage(vm: PsyMapViewModel) {
    var selectedSubject by remember { mutableStateOf<Subject?>(null) }
    var selectedType by remember { mutableStateOf<QuestionType?>(null) }
    var showBankDetail by remember { mutableStateOf(false) }
    var selectedBankId by remember { mutableStateOf("") }
    var filterFrequent by remember { mutableStateOf(false) }
    var filterMemorize by remember { mutableStateOf(false) }

    // 切换科目时重置题型筛选
    LaunchedEffect(selectedSubject) { selectedType = null }

    // 根据选中科目动态获取可用题型
    val availableTypes: List<QuestionType> = when {
        selectedSubject != null -> selectedSubject!!.availableQuestionTypes().filter { it != QuestionType.MULTI_CHOICE }
        else -> QuestionType.entries.filter { it != QuestionType.MULTI_CHOICE }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧科目分类栏
        Column(
            modifier = Modifier
                .width(80.dp)
                .fillMaxHeight()
                .background(Color(0xFFF5F5F5))
        ) {
            SubjectSideItem(label = "全部", selected = selectedSubject == null,
                onClick = { selectedSubject = null })
            Subject.entries.forEach { subject ->
                SubjectSideItem(label = subject.label, selected = selectedSubject == subject,
                    onClick = { selectedSubject = subject })
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(8.dp))

            // 标签筛选
            Text("标签", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 12.dp))
            SubjectSideItem(label = "🔥常考", selected = filterFrequent,
                onClick = { filterFrequent = !filterFrequent; if (filterFrequent) filterMemorize = false })
            SubjectSideItem(label = "📖多背", selected = filterMemorize,
                onClick = { filterMemorize = !filterMemorize; if (filterMemorize) filterFrequent = false })
        }

        // 右侧
        val filteredBanks = vm.questionBanks.filter { bank ->
            selectedSubject == null || bank.subject == selectedSubject
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White)
        ) {
            // 题型筛选标签 — 根据科目动态显示
            item {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedType == null,
                        onClick = { selectedType = null },
                        label = { Text("全部题型", fontSize = 12.sp) }
                    )
                    availableTypes.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = if (selectedType == type) null else type },
                            label = { Text(type.label, fontSize = 12.sp) }
                        )
                    }
                }
            }

            // 当前筛选提示
            if (selectedSubject != null) {
                item {
                    Text(
                        "${selectedSubject!!.emoji} ${selectedSubject!!.label}  ·  可用题型: ${availableTypes.joinToString("、") { it.label }}",
                        fontSize = 11.sp, color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            items(filteredBanks) { bank ->
                val questions = vm.getQuestionsForBank(bank.id).filter { q ->
                    (selectedType == null || q.type == selectedType) &&
                    (!filterFrequent || q.isFrequent) &&
                    (!filterMemorize || q.isMemorize)
                }

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            selectedBankId = bank.id
                            showBankDetail = true
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(bank.subject.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(bank.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null,
                            tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    // 显示该题库的可用题型
                    Text(
                        "${questions.size} 题  ·  错题 ${questions.count { it.isInWrongBook }}  ·  ${bank.subject.availableQuestionTypes().joinToString("/") { it.label }}",
                        fontSize = 11.sp, color = Color.Gray
                    )
                    HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = Color(0xFFF0F0F0))
                }
            }

            if (filteredBanks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("暂无题库", color = Color.Gray)
                    }
                }
            }
        }
    }

    if (showBankDetail && selectedBankId.isNotBlank()) {
        QuestionBankDetailSheet(
            vm = vm,
            bankId = selectedBankId,
            onDismiss = { showBankDetail = false }
        )
    }
}

@Composable
fun SubjectSideItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Color.White else Color.Transparent)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color(0xFFEF6C00) else Color(0xFF666666))
    }
}
