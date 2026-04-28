package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
    val sectors by vm.sectors.collectAsState()
    var sectorsExpanded by remember { mutableStateOf(false) }

    // 搜索结果（只在有搜索词时显示）
    val hasSearch = query.isNotBlank()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FundBg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 标题
        item {
            Text("发现", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        }

        // 搜索栏
        item {
            OutlinedTextField(
                value = query, onValueChange = { vm.search(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("输入基金代码查询（如 004320）", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedBorderColor = FundBlue, unfocusedBorderColor = Color(0xFFE0E0E0))
            )
            Spacer(Modifier.height(8.dp))
        }

        // Loading提示
        if (isLoading) {
            item {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = FundBlue)
                Text("正在获取AI预测评分...", fontSize = 12.sp, color = FundBlue,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }

        // 搜索结果
        if (hasSearch) {
            if (funds.isEmpty() && !isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center) {
                        Text("未找到基金，请检查代码是否正确", fontSize = 14.sp, color = FundTextSecondary)
                    }
                }
            }
            items(funds, key = { it.code }) { fund ->
                SearchResultCard(fund = fund, onClick = { onFundClick(fund) })
            }
        }

        // 无搜索时显示行业板块
        if (!hasSearch) {
            // 板块标题
            item {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("📊", fontSize = 18.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("概念板块涨幅", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (sectors.size > 10) {
                        Text(if (sectorsExpanded) "收起 ▲" else "展开全部 ▼",
                            fontSize = 12.sp, color = FundBlue,
                            modifier = Modifier.clickable { sectorsExpanded = !sectorsExpanded })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            val displaySectors = if (sectorsExpanded) sectors else sectors.take(10)
            itemsIndexed(displaySectors) { index, sector ->
                Row(modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", fontSize = 12.sp, color = FundTextSecondary,
                        modifier = Modifier.width(24.dp))
                    Text(sector.name, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(formatChange(sector.changePct), fontSize = 13.sp,
                        color = changeColor(sector.changePct), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(fund: Fund, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fund.name, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fund.code, fontSize = 12.sp, color = FundTextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("%.4f".format(fund.nav), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("最新净值", fontSize = 11.sp, color = FundTextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatChange(fund.dayChange), fontSize = 16.sp,
                        color = changeColor(fund.dayChange), fontWeight = FontWeight.Medium)
                    Text("日涨幅", fontSize = 11.sp, color = FundTextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (fund.aiScore > 0) {
                        Text("${fund.aiScore}%", fontSize = 18.sp,
                            color = FundBlue, fontWeight = FontWeight.Bold)
                        Text("AI预测", fontSize = 11.sp, color = FundTextSecondary)
                    } else {
                        Text("--", fontSize = 18.sp, color = FundTextSecondary)
                        Text("点击查看", fontSize = 11.sp, color = FundBlue)
                    }
                }
            }
        }
    }
}
