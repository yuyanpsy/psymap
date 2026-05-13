package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FundFavoritesPage(vm: FundPickerViewModel, onFundClick: (Fund) -> Unit) {
    val favorites by vm.favorites.collectAsState()
    val predictions by vm.aiPredictions.collectAsState()
    val positions by vm.positions.collectAsState()
    val posCodes = remember(positions) { positions.map { it.fundCode }.toSet() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FundBg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("我的自选", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
        }

        if (favorites.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = FundCardBg)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val up = favorites.count { it.dayChange > 0 }
                        val down = favorites.count { it.dayChange < 0 }
                        val flat = favorites.size - up - down
                        StatItem("关注", "${favorites.size}", FundTextPrimary)
                        StatItem("今日涨", "$up", FundRed)
                        StatItem("今日跌", "$down", FundGreen)
                        StatItem("平", "$flat", FundTextPrimary)
                    }
                }
            }
        }

        if (favorites.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("⭐", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("暂无自选基金", fontSize = 16.sp, color = FundTextSecondary)
                        Text("在基金详情页点击☆添加关注",
                            fontSize = 13.sp, color = FundTextSecondary)
                    }
                }
            }
        }

        items(favorites, key = { it.code }) { fund ->
            val pred = predictions[fund.code]
            val aiScore = (pred?.get("probability") as? Double)?.toInt() ?: 0
            val conf = (pred?.get("confidence") as? Double)?.toInt() ?: 0
            val sharpe = (pred?.get("sharpe") as? Double) ?: 0.0
            val maxDd = (pred?.get("max_drawdown") as? Double) ?: 100.0
            val posPct = (pred?.get("positive_pct") as? Double) ?: 0.0
            FavoriteItem(
                fund = fund, aiScore = aiScore,
                confidence = conf, sharpe = sharpe,
                maxDrawdown = maxDd, positivePct = posPct,
                isPositioned = fund.code in posCodes,
                onToggleFavorite = { vm.toggleFavorite(fund.code) },
                onClick = { onFundClick(fund) }
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = FundTextSecondary)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun FavoriteItem(
    fund: Fund, aiScore: Int,
    confidence: Int = 0,
    sharpe: Double = 0.0,
    maxDrawdown: Double = 100.0,
    positivePct: Double = 0.0,
    isPositioned: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = FundCardBg),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            // 统一头部：【板块】【基金名称】【基金代码】【持仓】【⭐】
            FundHeaderRow(
                fundName = fund.name, fundCode = fund.code,
                isFavorite = true,  // 自选页里都是已收藏
                isPositioned = isPositioned,
                onFavoriteToggle = onToggleFavorite,
                goldenWhenHighAi = true, aiScore = aiScore,
                confidence = confidence, sharpe = sharpe,
                maxDrawdown = maxDrawdown, positivePct = positivePct,
                nameFontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            // 底行：左侧净值率，右侧AI
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 左：今日涨跌
                Text("今日 ", fontSize = 12.sp, color = FundTextSecondary)
                Text(formatChange(fund.dayChange), fontSize = 13.sp,
                    color = changeColor(fund.dayChange), fontWeight = FontWeight.Medium)
                if (fund.nav > 0) {
                    Spacer(Modifier.width(10.dp))
                    Text("净值 %.4f".format(fund.nav), fontSize = 11.sp,
                        color = FundTextSecondary)
                }
                Spacer(Modifier.weight(1f))
                // 右：AI 预测率（统一蓝色）
                val displayAi = if (aiScore > 0) "AI $aiScore%" else "AI --"
                Text(displayAi, fontSize = 13.sp, color = FundBlue, fontWeight = FontWeight.Medium)
            }
        }
    }
}
