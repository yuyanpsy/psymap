package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FundHomePage(
    vm: FundPickerViewModel,
    onFundClick: (Fund) -> Unit,
    onIndexClick: (MarketIndex) -> Unit = {}
) {
    val market by vm.market.collectAsState()
    val topFunds by vm.topFunds.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val errorMsg by vm.errorMsg.collectAsState()
    val favorites by vm.favorites.collectAsState()
    val positions by vm.positions.collectAsState()
    val predictions by vm.aiPredictions.collectAsState()
    val favCodes = remember(favorites) { favorites.map { it.code }.toSet() }
    val posCodes = remember(positions) { positions.map { it.fundCode }.toSet() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FundBg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 加载/错误状态
        if (isLoading && topFunds.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⏳", fontSize = 32.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载实时数据...", fontSize = 14.sp, color = FundTextSecondary)
                    }
                }
            }
        } else if (errorMsg != null && topFunds.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("😕", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("数据加载失败", fontSize = 16.sp)
                        Text(errorMsg ?: "", fontSize = 12.sp, color = FundTextSecondary)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.loadRealData() }) { Text("重试") }
                    }
                }
            }
        } else {
        // 大盘指数（每列可点击）
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    market.indices.forEach { idx ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onIndexClick(idx) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(idx.name, fontSize = 11.sp, color = FundTextSecondary)
                            val valueStr = if (idx.name.contains("黄金") || idx.value < 1000)
                                "%.2f".format(idx.value) else "%.0f".format(idx.value)
                            Text(valueStr, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(formatChange(idx.changePct), fontSize = 12.sp,
                                color = changeColor(idx.changePct))
                        }
                    }
                }
            }
        }

        // AI精选标题
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("AI精选 · 今日 TOP10", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }

        // AI精选列表
        itemsIndexed(topFunds) { index, fund ->
            val pred = predictions[fund.code]
            val conf = (pred?.get("confidence") as? Double)?.toInt() ?: 0
            val sharpe = (pred?.get("sharpe") as? Double) ?: 0.0
            val maxDd = (pred?.get("max_drawdown") as? Double) ?: 100.0
            val posPct = (pred?.get("positive_pct") as? Double) ?: 0.0
            AiTopFundCard(
                rank = index + 1,
                fund = fund,
                isFavorite = fund.code in favCodes,
                isPositioned = fund.code in posCodes,
                confidence = conf,
                sharpe = sharpe,
                maxDrawdown = maxDd,
                positivePct = posPct,
                onFavoriteToggle = {
                    vm.repo.ensureFundCached(fund)
                    vm.toggleFavorite(fund.code)
                },
                onClick = { onFundClick(fund) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        } // end else
    }
}

@Composable
private fun AiTopFundCard(
    rank: Int, fund: Fund,
    isFavorite: Boolean, isPositioned: Boolean,
    confidence: Int = 0,
    sharpe: Double = 0.0,
    maxDrawdown: Double = 100.0,
    positivePct: Double = 0.0,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 排名
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (rank) {
                            1 -> Color(0xFFFFD700)
                            2 -> Color(0xFFC0C0C0)
                            3 -> Color(0xFFCD7F32)
                            else -> Color(0xFFE8EAED)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("$rank", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = if (rank <= 3) Color.White else FundTextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // 统一头部：【板块】【基金名称】【基金代码】【持仓】【⭐】
                FundHeaderRow(
                    fundName = fund.name, fundCode = fund.code,
                    isFavorite = isFavorite, isPositioned = isPositioned,
                    onFavoriteToggle = onFavoriteToggle,
                    goldenWhenHighAi = true, aiScore = fund.aiScore,
                    confidence = confidence,
                    sharpe = sharpe, maxDrawdown = maxDrawdown, positivePct = positivePct,
                    nameFontSize = 14.sp
                )
                Spacer(Modifier.height(8.dp))
                // 第二行：左侧净值率，右侧AI预测率
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 左：近30天（如果还没加载则显示近1年）
                    val (changeValue, changeLabel) = when {
                        fund.monthChange != 0.0 -> fund.monthChange to "近30天"
                        fund.yearChange != 0.0 -> fund.yearChange to "近1年"
                        else -> 0.0 to "近30天"
                    }
                    Text("$changeLabel ", fontSize = 12.sp, color = FundTextSecondary)
                    if (changeValue != 0.0) {
                        Text(formatChange(changeValue), fontSize = 13.sp,
                            color = changeColor(changeValue),
                            fontWeight = FontWeight.Medium)
                    } else {
                        Text("--", fontSize = 13.sp, color = FundTextSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    // 右：AI 预测率（统一蓝色，金色只用于基金名称）
                    Text("AI ${fund.aiScore}%", fontSize = 13.sp,
                        color = FundBlue, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
