package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FundDiscoverPage(vm: FundPickerViewModel, onFundClick: (Fund) -> Unit) {
    val funds by vm.funds.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var selectedType by remember { mutableStateOf("全部") }
    var selectedPeriod by remember { mutableStateOf("近6月") }
    var selectedSort by remember { mutableStateOf("收益率排序") }
    var ascending by remember { mutableStateOf(false) }
    val typeMap = mapOf("全部" to "all", "股票型" to "gp", "混合型" to "hh",
        "债券型" to "zq", "指数型" to "zs", "QDII" to "qdii", "LOF" to "lof")

    val sortedFunds = remember(funds, selectedSort, selectedPeriod, ascending) {
        val sorted = when (selectedSort) {
            "AI评分排序" -> funds.sortedByDescending { it.aiScore }
            "净值排序" -> funds.sortedByDescending { it.nav }
            "日涨幅排序" -> funds.sortedByDescending { it.dayChange }
            else -> when (selectedPeriod) {
                "近1周" -> funds.sortedByDescending { it.weekChange }
                "近1月" -> funds.sortedByDescending { it.monthChange }
                "近3月" -> funds.sortedByDescending { it.threeMonthChange }
                "近1年" -> funds.sortedByDescending { it.yearChange }
                else -> funds.sortedByDescending { it.sixMonthChange }
            }
        }
        if (ascending) sorted.reversed() else sorted
    }
    Column(modifier = Modifier.fillMaxSize().background(FundBg)) {
        Text("发现", fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        OutlinedTextField(
            value = query, onValueChange = { vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索基金名称/代码", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp), singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                focusedBorderColor = FundBlue, unfocusedBorderColor = Color(0xFFE0E0E0))
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            typeMap.keys.forEach { type ->
                FilterChip(selected = selectedType == type,
                    onClick = { selectedType = type; vm.loadRealDataByType(typeMap[type] ?: "all") },
                    label = { Text(type, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FundBlue, selectedLabelColor = Color.White))
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            var pe by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { pe = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)) { Text("$selectedPeriod ▾", fontSize = 12.sp) }
                DropdownMenu(expanded = pe, onDismissRequest = { pe = false }) {
                    listOf("近1周","近1月","近3月","近6月","近1年").forEach { o ->
                        DropdownMenuItem(text = { Text(o) }, onClick = { selectedPeriod = o; pe = false })
                    }
                }
            }
            var se by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { se = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)) { Text("$selectedSort ▾", fontSize = 12.sp) }
                DropdownMenu(expanded = se, onDismissRequest = { se = false }) {
                    listOf("收益率排序","AI评分排序","净值排序","日涨幅排序").forEach { o ->
                        DropdownMenuItem(text = { Text(o) }, onClick = { selectedSort = o; se = false })
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { ascending = !ascending },
                contentPadding = PaddingValues(horizontal = 6.dp)) {
                Text(if (ascending) "↑升序" else "↓降序", fontSize = 12.sp, color = FundBlue)
            }
            Text("共${sortedFunds.size}只", fontSize = 11.sp, color = FundTextSecondary)
        }
        Spacer(Modifier.height(4.dp))
        if (isLoading && funds.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FundBlue)
            }
        } else {
            val pChange: (Fund) -> Double = { f -> when (selectedPeriod) {
                "近1周" -> f.weekChange; "近1月" -> f.monthChange; "近3月" -> f.threeMonthChange
                "近1年" -> f.yearChange; else -> f.sixMonthChange } }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sortedFunds, key = { it.code }) { fund ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp),
                        onClick = { onFundClick(fund) }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(fund.name, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(fund.code, fontSize = 11.sp, color = FundTextSecondary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column { Text(formatChange(pChange(fund)), fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold, color = changeColor(pChange(fund)))
                                    Text("${selectedPeriod}收益", fontSize = 11.sp, color = FundTextSecondary) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(formatChange(fund.dayChange), fontSize = 14.sp, color = changeColor(fund.dayChange))
                                    Text("日涨幅", fontSize = 11.sp, color = FundTextSecondary) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("%.4f".format(fund.nav), fontSize = 14.sp)
                                    Text("净值", fontSize = 11.sp, color = FundTextSecondary) }
                                Column(horizontalAlignment = Alignment.End) {
                                    val realScore = fund.aiScore
                                    if (realScore < 70) {
                                        Text("${realScore}%", fontSize = 14.sp, color = FundBlue, fontWeight = FontWeight.Medium)
                                        Text("AI预测", fontSize = 11.sp, color = FundTextSecondary)
                                    } else {
                                        Text("--", fontSize = 14.sp, color = FundTextSecondary)
                                        Text("待预测", fontSize = 11.sp, color = FundTextSecondary)
                                    } }
                            }
                        }
                    }
                }
                if (sortedFunds.isEmpty()) { item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text("暂无匹配基金", fontSize = 14.sp, color = FundTextSecondary) } } }
            }
        }
    }
}
