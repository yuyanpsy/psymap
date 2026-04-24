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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FundDiscoverPage(vm: FundPickerViewModel, onFundClick: (Fund) -> Unit) {
    val funds by vm.funds.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    var selectedType by remember { mutableStateOf("全部") }

    // 基金类型映射到API参数
    val typeMap = mapOf(
        "全部" to "all", "股票型" to "gp", "混合型" to "hh",
        "债券型" to "zq", "指数型" to "zs", "QDII" to "qdii", "LOF" to "lof"
    )

    Column(modifier = Modifier.fillMaxSize().background(FundBg)) {
        Text("发现", fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))

        // 搜索栏
        OutlinedTextField(
            value = query,
            onValueChange = { vm.search(it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索基金名称/代码", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                focusedBorderColor = FundBlue, unfocusedBorderColor = Color(0xFFE0E0E0)
            )
        )

        Spacer(Modifier.height(12.dp))

        // 类型筛选 — 横向滚动，解决排列混乱问题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            typeMap.keys.forEach { type ->
                FilterChip(
                    selected = selectedType == type,
                    onClick = {
                        selectedType = type
                        // 切换类型时重新从API加载对应类型的基金
                        val apiType = typeMap[type] ?: "all"
                        vm.repo.fetchRealFundRank(
                            fundType = apiType,
                            pageSize = 100,
                            onResult = { newFunds ->
                                vm.search("") // 清空搜索
                                // 通过 refresh 更新
                            },
                            onError = { }
                        )
                        vm.loadRealDataByType(apiType)
                    },
                    label = { Text(type, fontSize = 13.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FundBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 基金列表
        if (isLoading && funds.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FundBlue)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(funds, key = { it.code }) { fund ->
                    FundListItem(fund = fund, onClick = { onFundClick(fund) })
                }
                if (funds.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(48.dp),
                            contentAlignment = Alignment.Center) {
                            Text("暂无匹配基金", fontSize = 14.sp, color = FundTextSecondary)
                        }
                    }
                }
                // 数据量提示
                item {
                    Text("共 ${funds.size} 只基金", fontSize = 12.sp,
                        color = FundTextSecondary,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
