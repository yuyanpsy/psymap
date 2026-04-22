package com.psymap.app

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoverPage(vm: PsyMapViewModel) {
    var selectedBankName by remember { mutableStateOf<String?>(null) }
    var selectedTypes by remember { mutableStateOf(setOf<QuestionType>()) }
    var showBankDetail by remember { mutableStateOf(false) }
    var selectedBankId by remember { mutableStateOf("") }
    var filterFrequent by remember { mutableStateOf(false) }
    var filterMemorize by remember { mutableStateOf(false) }

    val selectedBank = if (selectedBankName != null) vm.questionBanks.find { it.id == selectedBankName } else null
    val availableTypes: List<QuestionType> = when {
        selectedBank != null -> selectedBank.subject.availableQuestionTypes()
        else -> QuestionType.entries.toList()
    }

    // 计算当前筛选范围内每个题型实际有多少题
    val filteredBanks = vm.questionBanks.filter { selectedBankName == null || it.id == selectedBankName }
    val allFilteredQuestions = filteredBanks.flatMap { vm.getQuestionsForBank(it.id) }
    val typeCounts = availableTypes.associateWith { type -> allFilteredQuestions.count { it.type == type } }

    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧分类栏
        Column(
            modifier = Modifier.width(96.dp).fillMaxHeight()
                .background(Color(0xFFF5F5F5)).verticalScroll(rememberScrollState())
        ) {
            SubjectSideItem(label = "全部", selected = selectedBankName == null,
                onClick = { selectedBankName = null; selectedTypes = emptySet() })
            vm.questionBanks.forEach { bank ->
                SubjectSideItem(label = bank.name, selected = selectedBankName == bank.id,
                    onClick = { selectedBankName = bank.id; selectedTypes = emptySet() })
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
            Spacer(Modifier.height(8.dp))
            Text("标签", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 12.dp))
            // 常考和多背可同时选中
            SubjectSideItem(label = "🔥常考", selected = filterFrequent,
                onClick = { filterFrequent = !filterFrequent })
            SubjectSideItem(label = "📖多背", selected = filterMemorize,
                onClick = { filterMemorize = !filterMemorize })
        }

        // 右侧
        LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight().background(Color.White)) {
            // 题型筛选标签（固定在顶部）
            stickyHeader {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 全部题型
                    FilterChip(
                        selected = selectedTypes.isEmpty(),
                        onClick = { selectedTypes = emptySet() },
                        label = { Text("全部题型", fontSize = 12.sp) },
                        shape = RoundedCornerShape(20.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFF8A00),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF666666)
                        )
                    )
                    availableTypes.forEach { type ->
                        val hasQuestions = (typeCounts[type] ?: 0) > 0
                        FilterChip(
                            selected = type in selectedTypes,
                            onClick = {
                                if (hasQuestions) {
                                    selectedTypes = if (type in selectedTypes) selectedTypes - type else selectedTypes + type
                                }
                            },
                            label = { Text(type.label, fontSize = 12.sp) },
                            enabled = hasQuestions,
                            shape = RoundedCornerShape(20.dp),
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFF8A00),
                                selectedLabelColor = Color.White,
                                containerColor = Color.White,
                                labelColor = Color(0xFF666666),
                                disabledContainerColor = Color(0xFFF5F5F5),
                                disabledLabelColor = Color(0xFFCCCCCC)
                            )
                        )
                    }
                }
            }

            // 筛选提示
            if (selectedTypes.isNotEmpty() || filterFrequent || filterMemorize) {
                item {
                    val filters = mutableListOf<String>()
                    if (selectedTypes.isNotEmpty()) filters.add(selectedTypes.joinToString("+") { it.label })
                    if (filterFrequent) filters.add("常考")
                    if (filterMemorize) filters.add("多背")
                    Text("筛选: ${filters.joinToString(" · ")}", fontSize = 11.sp, color = Color(0xFF1976D2),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                }
            }

            items(filteredBanks, key = { it.id }) { bank ->
                val allBankQuestions = vm.getQuestionsForBank(bank.id)
                val questions = allBankQuestions.filter { q ->
                    (selectedTypes.isEmpty() || q.type in selectedTypes) &&
                    (!filterFrequent || q.isFrequent) &&
                    (!filterMemorize || q.isMemorize)
                }
                val totalCount = allBankQuestions.size

                Column(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { selectedBankId = bank.id; showBankDetail = true }
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${questions.size}题", fontSize = 13.sp, color = Color(0xFFFF8A00), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("共${totalCount}题  ·  错题${questions.count { it.isInWrongBook }}",
                        fontSize = 11.sp, color = Color.Gray)
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

    // 点击题库时传递筛选条件
    if (showBankDetail && selectedBankId.isNotBlank()) {
        QuestionBankDetailSheet(
            vm = vm,
            bankId = selectedBankId,
            onDismiss = { showBankDetail = false },
            filterTypes = selectedTypes,
            filterFrequent = filterFrequent,
            filterMemorize = filterMemorize
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
            color = if (selected) Color(0xFFFF8A00) else Color(0xFF666666))
    }
}
