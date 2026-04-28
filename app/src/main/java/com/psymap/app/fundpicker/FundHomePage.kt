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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FundHomePage(vm: FundPickerViewModel, onFundClick: (Fund) -> Unit) {
    val market by vm.market.collectAsState()
    val topFunds by vm.topFunds.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val errorMsg by vm.errorMsg.collectAsState()

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
                        CircularProgressIndicator(color = FundBlue)
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
        // 市场情绪卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("市场情绪：", fontSize = 14.sp, color = FundTextSecondary)
                        Text(market.sentimentLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                market.sentimentScore >= 70 -> "😊"
                                market.sentimentScore >= 40 -> "😐"
                                else -> "😟"
                            }, fontSize = 16.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Text("${market.sentimentScore}/100", fontSize = 13.sp, color = FundBlue)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { market.sentimentScore / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = FundBlue, trackColor = Color(0xFFE8EAED),
                        gapSize = 0.dp, drawStopIndicator = {}
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        market.indices.forEach { idx ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(idx.name, fontSize = 12.sp, color = FundTextSecondary)
                                Text("%.0f".format(idx.value), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(formatChange(idx.changePct), fontSize = 12.sp,
                                    color = changeColor(idx.changePct))
                            }
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
                Text("🔥", fontSize = 18.sp)
                Spacer(Modifier.width(4.dp))
                Text("AI精选 · 今日Top10", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }

        // AI精选列表
        itemsIndexed(topFunds) { index, fund ->
            AiTopFundCard(
                rank = index + 1,
                fund = fund,
                onClick = { onFundClick(fund) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        } // end else
    }
}

@Composable
private fun AiTopFundCard(rank: Int, fund: Fund, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fund.name, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Text(fund.code, fontSize = 11.sp, color = FundTextSecondary)
                }
                Spacer(Modifier.height(6.dp))
                AiScoreBar(fund.aiScore)
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("近1月 ", fontSize = 11.sp, color = FundTextSecondary)
                    Text(formatChange(fund.monthChange), fontSize = 11.sp,
                        color = changeColor(fund.monthChange))
                    Spacer(Modifier.width(16.dp))
                    Text("近1年 ", fontSize = 11.sp, color = FundTextSecondary)
                    Text(formatChange(fund.yearChange), fontSize = 11.sp,
                        color = changeColor(fund.yearChange))
                }
            }
        }
    }
}
