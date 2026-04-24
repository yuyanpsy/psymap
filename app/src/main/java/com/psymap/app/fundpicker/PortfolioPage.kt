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
    val (totalValue, totalProfit, totalProfitPct) = vm.getPortfolioSummary()

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
            PositionCard(
                position = pos,
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
    onClick: () -> Unit,
    onSell: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(position.fundName, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f))
                Text(position.fundCode, fontSize = 12.sp, color = FundTextSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("持仓 ¥%.0f".format(position.currentValue), fontSize = 13.sp)
                    Text("占比 %.1f%%".format(position.weightPct), fontSize = 11.sp, color = FundTextSecondary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("成本 %.4f".format(position.avgCostNav), fontSize = 13.sp)
                    Text("现价 %.4f".format(position.currentNav), fontSize = 11.sp, color = FundTextSecondary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${if (position.profit >= 0) "+" else ""}¥%.0f".format(position.profit),
                        fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = changeColor(position.profit)
                    )
                    Text(formatChange(position.profitPct), fontSize = 12.sp,
                        color = changeColor(position.profitPct))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSell,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("卖出", fontSize = 12.sp) }
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
            color = if (txn.type == "buy") FundRed else FundGreen,
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
