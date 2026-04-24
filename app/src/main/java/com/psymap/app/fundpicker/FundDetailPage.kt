package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FundDetailPage(
    vm: FundPickerViewModel, onBack: () -> Unit,
    onOpenAiLab: () -> Unit, onBuy: () -> Unit
) {
    val fund by vm.selectedFund.collectAsState()
    val navHistory by vm.navHistory.collectAsState()
    val prediction by vm.prediction.collectAsState()
    val chartPeriod by vm.chartPeriod.collectAsState()
    val predPeriod by vm.predictionPeriod.collectAsState()
    val estimate by vm.estimate.collectAsState()
    val detail by vm.fundDetail.collectAsState()
    val overview by vm.fundOverview.collectAsState()
    val f = fund ?: return

    var showBuyDialog by remember { mutableStateOf(false) }
    val isFav = vm.isFavorite(f.code)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("基金详情", fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showBuyDialog = true }) {
                        Text("模拟买入", color = FundBlue, fontSize = 13.sp)
                    }
                    IconButton(onClick = { vm.toggleFavorite(f.code) }) {
                        Icon(
                            if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "关注",
                            tint = if (isFav) Color(0xFFFFB300) else FundTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(FundBg),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 基本信息 + 实时估值
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(f.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Row {
                            Text("${f.code}", fontSize = 13.sp, color = FundTextSecondary)
                            detail?.let { d ->
                                if (d.fundType.isNotBlank()) Text(" · ${d.fundType}", fontSize = 13.sp, color = FundTextSecondary)
                                if (d.setupDate.isNotBlank()) Text(" · 成立${d.setupDate}", fontSize = 13.sp, color = FundTextSecondary)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        val est = estimate
                        if (est != null && est.estimateNav > 0) {
                            Text("实时估值", fontSize = 12.sp, color = FundTextSecondary)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("%.4f".format(est.estimateNav), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(12.dp))
                                Text(formatChange(est.estimateChangePct), fontSize = 16.sp,
                                    color = changeColor(est.estimateChangePct), fontWeight = FontWeight.Medium)
                            }
                            Text("估值时间: ${est.estimateTime}  |  昨日净值: %.4f".format(est.nav),
                                fontSize = 11.sp, color = FundTextSecondary)
                        } else {
                            Text("最新净值", fontSize = 12.sp, color = FundTextSecondary)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("%.4f".format(f.nav), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(12.dp))
                                Text(formatChange(f.dayChange), fontSize = 16.sp,
                                    color = changeColor(f.dayChange), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            // 基金信息卡片（规模、费率等）
            detail?.let { d ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("📋 基金信息", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            if (d.fundScale.isNotBlank()) InfoRow("基金规模", d.fundScale)
                            if (d.setupDate.isNotBlank()) InfoRow("成立日期", d.setupDate)
                            if (d.fundType.isNotBlank()) InfoRow("基金类型", d.fundType)
                            if (d.managerName.isNotBlank()) InfoRow("基金经理", d.managerName)
                            if (d.managerWorkTime.isNotBlank()) InfoRow("从业时间", d.managerWorkTime)
                            if (d.managerSize.isNotBlank()) InfoRow("管理规模", d.managerSize)
                            if (d.buyRate.isNotBlank()) {
                                InfoRow("申购费率", if (d.buyRateDiscount.isNotBlank() && d.buyRateDiscount != d.buyRate)
                                    "${d.buyRate} → ${d.buyRateDiscount}（折扣）" else d.buyRate)
                            }
                            // 资产配置
                            if (d.stockRatio.isNotBlank()) InfoRow("股票占比", "${d.stockRatio}%")
                            if (d.bondRatio.isNotBlank() && d.bondRatio != "0") InfoRow("债券占比", "${d.bondRatio}%")
                            if (d.cashRatio.isNotBlank()) InfoRow("现金占比", "${d.cashRatio}%")
                            if (d.instHoldRatio.isNotBlank()) InfoRow("机构持有", "${d.instHoldRatio}%")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // 费率信息（来自概况页，完整费率）
            overview?.let { ov ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("💰 费率信息", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            if (ov.manageFeeRate.isNotBlank()) InfoRow("管理费率", ov.manageFeeRate)
                            if (ov.custodianFeeRate.isNotBlank()) InfoRow("托管费率", ov.custodianFeeRate)
                            if (ov.salesServiceFeeRate.isNotBlank()) InfoRow("销售服务费", ov.salesServiceFeeRate)
                            if (ov.maxBuyRate.isNotBlank()) InfoRow("最高申购费率", ov.maxBuyRate)
                            if (ov.maxRedeemRate.isNotBlank()) InfoRow("最高赎回费率", ov.maxRedeemRate)
                            if (ov.scale.isNotBlank()) InfoRow("净资产规模", ov.scale)
                            if (ov.custodian.isNotBlank()) InfoRow("基金托管人", ov.custodian)
                            if (ov.benchmark.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text("业绩比较基准", fontSize = 12.sp, color = FundTextSecondary)
                                Text(ov.benchmark, fontSize = 11.sp, color = FundTextSecondary, lineHeight = 15.sp)
                            }
                            if (ov.dividend.isNotBlank()) InfoRow("成立来分红", ov.dividend)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            // 净值走势（带日期和数值标注）
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("净值走势", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        TimePeriodSelector(
                            periods = listOf(TimePeriod.D7, TimePeriod.D30, TimePeriod.M3,
                                TimePeriod.M6, TimePeriod.Y1, TimePeriod.Y3),
                            selected = chartPeriod,
                            onSelect = { vm.setChartPeriod(it) }
                        )
                        Spacer(Modifier.height(8.dp))
                        if (navHistory.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(180.dp),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = FundBlue, modifier = Modifier.size(24.dp))
                            }
                        } else {
                            // 日期范围标注
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(navHistory.first().date, fontSize = 10.sp, color = FundTextSecondary)
                                Text("共${navHistory.size}个交易日", fontSize = 10.sp, color = FundTextSecondary)
                                Text(navHistory.last().date, fontSize = 10.sp, color = FundTextSecondary)
                            }
                            Spacer(Modifier.height(4.dp))
                            InteractiveNavChart(navHistory, modifier = Modifier.fillMaxWidth().height(180.dp))
                            // 最高最低标注
                            val maxP = navHistory.maxByOrNull { it.nav }
                            val minP = navHistory.minByOrNull { it.nav }
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("最低: %.4f (%s)".format(minP?.nav, minP?.date?.substring(5)),
                                    fontSize = 10.sp, color = FundGreen)
                                Text("最高: %.4f (%s)".format(maxP?.nav, maxP?.date?.substring(5)),
                                    fontSize = 10.sp, color = FundRed)
                            }
                        }
                    }
                }
            }

            // AI预测面板
            item {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖", fontSize = 18.sp)
                            Spacer(Modifier.width(6.dp))
                            Text("AI 预测面板", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("预测周期:", fontSize = 12.sp, color = FundTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        TimePeriodSelector(
                            periods = listOf(TimePeriod.D7, TimePeriod.D30, TimePeriod.M3, TimePeriod.M6, TimePeriod.Y1),
                            selected = predPeriod, onSelect = { vm.setPredictionPeriod(it) }
                        )
                        prediction?.let { pred ->
                            Spacer(Modifier.height(12.dp))
                            Text("未来${predPeriod.label}上涨概率", fontSize = 13.sp, color = FundTextSecondary)
                            Spacer(Modifier.height(4.dp))
                            AiScoreBar(pred.probability)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("预测置信度: ", fontSize = 13.sp, color = FundTextSecondary)
                                ConfidenceStars(pred.confidence)
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("关键因子:", fontSize = 13.sp, color = FundTextSecondary)
                            pred.factors.forEach { factor ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text("· ${factor.name}: ${factor.signal} ", fontSize = 13.sp)
                                    Text(when (factor.direction) { "up" -> "↑"; "down" -> "↓"; else -> "→" },
                                        color = when (factor.direction) { "up" -> FundRed; "down" -> FundGreen; else -> FundTextSecondary })
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { vm.runModelCompare(f.code, predPeriod); onOpenAiLab() },
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("AI模型对比（8种算法）", fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("模型: ${pred.modelName}  |  更新: ${pred.updatedAt}",
                                fontSize = 11.sp, color = FundTextSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("⚠️ AI预测仅供参考，不构成投资建议", fontSize = 11.sp, color = Color(0xFFFF8F00))
                    }
                }
            }

            // 业绩表现
            item {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("📊 业绩表现", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        PerformanceRow("近7天", f.weekChange)
                        PerformanceRow("近30天", f.monthChange)
                        PerformanceRow("近3月", f.threeMonthChange)
                        PerformanceRow("近6月", f.sixMonthChange)
                        PerformanceRow("近1年", f.yearChange)
                    }
                }
            }

            // 持仓分析
            detail?.let { d ->
                if (d.topStocks.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("🏢 前十大持仓", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(8.dp))
                                d.topStocks.forEachIndexed { i, stock ->
                                    Text("${i + 1}. $stock", fontSize = 13.sp,
                                        modifier = Modifier.padding(vertical = 2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showBuyDialog) {
        BuyDialog(fund = f, onConfirm = { amount ->
            vm.buy(f.code, amount); showBuyDialog = false; onBuy()
        }, onDismiss = { showBuyDialog = false })
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = FundTextSecondary)
        Text(value, fontSize = 13.sp, maxLines = 1,
            modifier = Modifier.widthIn(max = 220.dp))
    }
}

@Composable
private fun PerformanceRow(label: String, value: Double) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = FundTextSecondary)
        Text(formatChange(value), fontSize = 13.sp,
            color = changeColor(value), fontWeight = FontWeight.Medium)
    }
}

/** 可交互净值走势图：长按显示十字光标+具体日期和数值 */
@Composable
fun InteractiveNavChart(data: List<NavPoint>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val minNav = data.minOf { it.nav }
    val maxNav = data.maxOf { it.nav }
    val range = (maxNav - minNav).coerceAtLeast(0.0001)

    var touchIndex by remember { mutableIntStateOf(-1) }

    // 长按时显示的数据
    if (touchIndex in data.indices) {
        val p = data[touchIndex]
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.Center) {
            Text("${p.date}  净值: %.4f  涨跌: %s".format(p.nav, formatChange(p.changePct)),
                fontSize = 12.sp, fontWeight = FontWeight.Medium, color = FundBlue)
        }
    }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF8FAFF))
            .pointerInput(data) {
                detectTapGestures(
                    onPress = { offset ->
                        val idx = ((offset.x / size.width) * (data.size - 1)).toInt().coerceIn(0, data.size - 1)
                        touchIndex = idx
                        tryAwaitRelease()
                        touchIndex = -1
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val padding = 4.dp.toPx()

        // 画网格线
        for (i in 0..3) {
            val y = padding + (h - 2 * padding) * i / 3
            drawLine(Color(0xFFE8EAED), Offset(0f, y), Offset(w, y), strokeWidth = 0.5.dp.toPx())
        }

        // 画净值曲线
        val path = Path()
        data.forEachIndexed { i, point ->
            val x = w * i / (data.size - 1).coerceAtLeast(1)
            val y = padding + (h - 2 * padding) * (1 - ((point.nav - minNav) / range)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, FundBlue, style = Stroke(width = 2.dp.toPx()))

        // 长按时画十字光标
        if (touchIndex in data.indices) {
            val x = w * touchIndex / (data.size - 1).coerceAtLeast(1)
            val point = data[touchIndex]
            val y = padding + (h - 2 * padding) * (1 - ((point.nav - minNav) / range)).toFloat()
            drawLine(Color(0xFF1A73E8), Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            drawLine(Color(0xFF1A73E8), Offset(0f, y), Offset(w, y), strokeWidth = 0.5.dp.toPx())
            drawCircle(FundBlue, radius = 4.dp.toPx(), center = Offset(x, y))
            drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
        }
    }
}

@Composable
fun BuyDialog(fund: Fund, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val shares = if (fund.nav > 0) amount / fund.nav else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("模拟买入") },
        text = {
            Column {
                Text("${fund.name}  ${fund.code}", fontSize = 14.sp)
                Text("当前净值: %.4f".format(fund.nav), fontSize = 13.sp, color = FundTextSecondary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("买入金额 (元)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("1000", "5000", "10000", "50000").forEach { quick ->
                        OutlinedButton(onClick = { amountText = quick },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) { Text("¥$quick", fontSize = 12.sp) }
                    }
                }
                if (amount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text("预估份额: %.2f 份".format(shares), fontSize = 13.sp, color = FundTextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Text("⚠️ 这是模拟交易，不涉及真实资金", fontSize = 11.sp, color = Color(0xFFFF8F00))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(amount) }, enabled = amount >= 100) { Text("确认买入") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
