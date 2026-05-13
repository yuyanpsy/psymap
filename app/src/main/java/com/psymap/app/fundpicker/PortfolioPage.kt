package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PortfolioPage(vm: FundPickerViewModel, onFundClick: (String) -> Unit) {
    val positions by vm.positions.collectAsState()
    val transactions by vm.transactions.collectAsState()
    val predictions by vm.aiPredictions.collectAsState()
    // 从实时 positions 计算总收益（不用 getPortfolioSummary 的静态数据）
    val totalValue = positions.sumOf { it.currentValue }
    val totalCost = positions.sumOf { it.costAmount }
    val totalProfit = totalValue - totalCost
    val totalProfitPct = if (totalCost > 0) totalProfit / totalCost * 100 else 0.0

    var showSellDialog by remember { mutableStateOf<PortfolioPosition?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FundBg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 标题
        item {
            Text("模拟持仓", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp))
        }

        // 总资产卡片
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1A73E8), Color(0xFF4285F4))
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text("模拟总资产", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.height(4.dp))
                        Text("¥ %.2f".format(totalValue), fontSize = 28.sp,
                            fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("总收益", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                                Text(
                                    "${if (totalProfit >= 0) "+" else ""}¥%.2f".format(totalProfit),
                                    fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.White
                                )
                                Text(formatChange(totalProfitPct), fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("持仓数", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                                Text("${positions.size}", fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium, color = Color.White)
                                Text("只基金", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                            }
                        }
                    }
                }
            }
        }

        // 持仓明细标题
        item {
            Spacer(Modifier.height(16.dp))
            Text("持仓明细", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
        }

        if (positions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💰", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("暂无持仓", fontSize = 16.sp, color = FundTextSecondary)
                        Text("去「首页」或「发现」选择基金模拟买入",
                            fontSize = 13.sp, color = FundTextSecondary)
                    }
                }
            }
        }

        // 持仓列表
        items(positions, key = { it.fundCode }) { pos ->
            val pred = predictions[pos.fundCode]
            val aiScore = (pred?.get("probability") as? Double)?.toInt() ?: 0
            val conf = (pred?.get("confidence") as? Double)?.toInt() ?: 0
            val sharpe = (pred?.get("sharpe") as? Double) ?: 0.0
            val maxDd = (pred?.get("max_drawdown") as? Double) ?: 100.0
            val posPct = (pred?.get("positive_pct") as? Double) ?: 0.0
            val isFav = vm.isFavorite(pos.fundCode)
            PositionCard(
                position = pos,
                aiScore = aiScore,
                confidence = conf,
                sharpe = sharpe,
                maxDrawdown = maxDd,
                positivePct = posPct,
                isFavorite = isFav,
                onToggleFavorite = { vm.toggleFavorite(pos.fundCode) },
                onClick = { onFundClick(pos.fundCode) },
                onSell = { showSellDialog = pos },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // 交易记录
        if (transactions.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                Text("📋 最近交易", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(8.dp))
            }
            items(transactions.take(10), key = { it.id }) { txn ->
                TransactionItem(txn, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
        }
    }

    // 卖出弹窗
    showSellDialog?.let { pos ->
        SellDialog(
            position = pos,
            onConfirm = { amount, sellAll ->
                vm.sell(pos.fundCode, amount, sellAll)
                showSellDialog = null
            },
            onDismiss = { showSellDialog = null }
        )
    }
}

@Composable
private fun PositionCard(
    position: PortfolioPosition,
    aiScore: Int,
    confidence: Int = 0,
    sharpe: Double = 0.0,
    maxDrawdown: Double = 100.0,
    positivePct: Double = 0.0,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    onSell: () -> Unit,
    modifier: Modifier = Modifier
) {
    val holdingDays = remember(position.buyDate) {
        if (position.buyDate.isBlank()) 0 else try {
            val buyTime = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .parse(position.buyDate)
            if (buyTime != null) ((System.currentTimeMillis() - buyTime.time) / 86400000).toInt().coerceAtLeast(0)
            else 0
        } catch (e: Exception) { 0 }
    }
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.5.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            // 统一头部：【板块】【基金名称】【基金代码】【持仓】【⭐】
            FundHeaderRow(
                fundName = position.fundName, fundCode = position.fundCode,
                isFavorite = isFavorite, isPositioned = true,
                onFavoriteToggle = onToggleFavorite,
                goldenWhenHighAi = true, aiScore = aiScore,
                confidence = confidence, sharpe = sharpe,
                maxDrawdown = maxDrawdown, positivePct = positivePct,
                nameFontSize = 14.sp
            )

            Spacer(Modifier.height(8.dp))
            // 第二行：左侧收益金额+收益率，右侧"买入时AI / 当前AI"
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (position.profit >= 0) "+" else ""}¥%.2f".format(position.profit),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = changeColor(position.profit)
                )
                Spacer(Modifier.width(8.dp))
                Text(formatChange(position.profitPct), fontSize = 13.sp,
                    color = changeColor(position.profitPct), fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                // 买入时AI → 当前AI
                val buyAiStr = if (position.buyAiScore > 0) "${position.buyAiScore}%" else "--"
                val nowAiStr = if (aiScore > 0) "${aiScore}%" else "--"
                Text("AI $buyAiStr", fontSize = 12.sp, color = FundTextSecondary)
                Text(" → ", fontSize = 12.sp, color = FundTextSecondary)
                Text(nowAiStr, fontSize = 13.sp, color = FundBlue,
                    fontWeight = FontWeight.Medium)
            }

            Spacer(Modifier.height(8.dp))
            // 第三行：持仓明细（成本 现价 持仓市值）
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("成本 %.4f".format(position.avgCostNav), fontSize = 11.sp, color = FundTextSecondary)
                Spacer(Modifier.width(10.dp))
                Text("现价 %.4f".format(position.currentNav), fontSize = 11.sp, color = FundTextSecondary)
                Spacer(Modifier.weight(1f))
                Text("持仓 ¥%.0f".format(position.currentValue), fontSize = 11.sp, color = FundTextSecondary)
            }

            Spacer(Modifier.height(6.dp))
            // 第四行：持有天数 | 卖出按钮
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("持有 ${holdingDays}天", fontSize = 11.sp, color = FundTextSecondary)
                // AI 变化箭头
                val delta = aiScore - position.buyAiScore
                if (position.buyAiScore > 0 && aiScore > 0 && delta != 0) {
                    Spacer(Modifier.width(10.dp))
                    val arrow = if (delta > 0) "AI ↑${delta}" else "AI ↓${-delta}"
                    val color = if (delta > 0) FundRed else FundGreen
                    Text(arrow, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onSell,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp)
                ) { Text("卖出", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun TransactionItem(txn: Transaction, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(txn.createdAt, fontSize = 12.sp, color = FundTextSecondary,
            modifier = Modifier.width(80.dp))
        Text(
            if (txn.type == "buy") "买入" else "卖出",
            fontSize = 12.sp,
            color = if (txn.type == "buy") FundBlue else Color(0xFFFF9800),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(36.dp)
        )
        Text(txn.fundName, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
        Text("¥%.0f".format(txn.amount), fontSize = 12.sp, textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp))
    }
}

/** 卖出弹窗 */
@Composable
fun SellDialog(
    position: PortfolioPosition,
    onConfirm: (Double?, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    var sellAll by remember { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull() ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模拟卖出") },
        text = {
            Column {
                Text("${position.fundName}  ${position.fundCode}", fontSize = 14.sp)
                Text("当前净值: %.4f".format(position.currentNav), fontSize = 13.sp, color = FundTextSecondary)
                Text("持有份额: %.2f 份".format(position.shares), fontSize = 13.sp, color = FundTextSecondary)
                Text("持仓市值: ¥%.2f".format(position.currentValue), fontSize = 13.sp, color = FundTextSecondary)
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sellAll, onCheckedChange = { sellAll = it })
                    Text("全部卖出", fontSize = 14.sp)
                }

                if (!sellAll) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("卖出金额 (元)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("1/4" to 0.25, "1/3" to 0.33, "1/2" to 0.5).forEach { (label, ratio) ->
                            OutlinedButton(
                                onClick = { amountText = "%.0f".format(position.currentValue * ratio) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) { Text(label, fontSize = 12.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (sellAll) onConfirm(null, true)
                    else onConfirm(amount, false)
                },
                enabled = sellAll || amount >= 100
            ) { Text("确认卖出") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
