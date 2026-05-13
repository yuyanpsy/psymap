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
    onBuy: () -> Unit
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
    var isFav by remember(f.code) { mutableStateOf(vm.isFavorite(f.code)) }

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
                        // 统一头部：【板块】【基金名称】【基金代码】【持仓】【⭐】
                        val isPositioned = vm.positions.collectAsState().value.any { it.fundCode == f.code }
                        val predData = vm.aiPredictions.collectAsState().value[f.code]
                        val detailConf = (predData?.get("confidence") as? Double)?.toInt() ?: (prediction?.confidence ?: 0)
                        val detailSharpe = (predData?.get("sharpe") as? Double) ?: 0.0
                        val detailMaxDd = (predData?.get("max_drawdown") as? Double) ?: 100.0
                        val detailPosPct = (predData?.get("positive_pct") as? Double) ?: 0.0
                        FundHeaderRow(
                            fundName = f.name, fundCode = f.code,
                            isFavorite = isFav, isPositioned = isPositioned,
                            onFavoriteToggle = { vm.toggleFavorite(f.code); isFav = !isFav },
                            goldenWhenHighAi = true,
                            aiScore = prediction?.probability ?: 0,
                            confidence = detailConf,
                            sharpe = detailSharpe,
                            maxDrawdown = detailMaxDd,
                            positivePct = detailPosPct,
                            nameFontSize = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Row {
                            detail?.let { d ->
                                if (d.fundType.isNotBlank())
                                    Text(d.fundType, fontSize = 13.sp, color = FundTextSecondary)
                                if (d.setupDate.isNotBlank())
                                    Text(" · 成立${d.setupDate}", fontSize = 13.sp, color = FundTextSecondary)
                            }
                        }
                        Spacer(Modifier.height(14.dp))

                        // 核心区域：AI 预测 + 风险指标（从 Supabase 读取，和列表页同源）
                        prediction?.let { pred ->
                            val predData = vm.aiPredictions.collectAsState().value[f.code]
                            val dbSharpe = (predData?.get("sharpe") as? Double) ?: 0.0
                            val dbMaxDd = (predData?.get("max_drawdown") as? Double) ?: 100.0
                            val dbPosPct = (predData?.get("positive_pct") as? Double) ?: 0.0

                            Row(modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically) {
                                // 左侧：AI 预测概率 + 置信度
                                Column {
                                    Text("AI预测上涨概率", fontSize = 11.sp, color = FundTextSecondary)
                                    Text("${pred.probability}%", fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold, color = FundBlue)
                                    ConfidenceStars(pred.confidence)
                                }
                                Spacer(Modifier.weight(1f))
                                // 右侧：成立以来全量历史指标（和金色判断同源）
                                Column(horizontalAlignment = Alignment.End,
                                    modifier = Modifier.width(100.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("夏普", fontSize = 11.sp, color = FundTextSecondary)
                                        Text("%.2f".format(dbSharpe), fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold, color = FundBlue)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("回撤", fontSize = 11.sp, color = FundTextSecondary)
                                        Text("%.1f%%".format(dbMaxDd), fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold, color = FundBlue)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("胜率", fontSize = 11.sp, color = FundTextSecondary)
                                        Text("%.0f%%".format(dbPosPct), fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold, color = FundBlue)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            // 合并说明区域：数据来源 + 金色条件 + 历史回测
                            val backtestBuckets by vm.backtestBuckets.collectAsState()
                            val bucket = remember(pred.probability, backtestBuckets) {
                                backtestBuckets.firstOrNull { b ->
                                    val lo = (b["bucket_min"] as? Double)?.toInt() ?: -1
                                    val hi = (b["bucket_max"] as? Double)?.toInt() ?: -1
                                    pred.probability in lo until hi
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFF3F7FF))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text("* 基于成立以来全量历史数据计算",
                                        fontSize = 9.sp, color = FundTextSecondary, lineHeight = 13.sp)
                                    Text("* 金色标注条件：AI≥70% + 置信度≥4 + 夏普>2 + 回撤<15% + 正收益>80%",
                                        fontSize = 9.sp, color = FundTextSecondary, lineHeight = 13.sp)
                                    if (bucket != null) {
                                        val total = (bucket["total_count"] as? Double)?.toInt() ?: 0
                                        val wins = (bucket["win_count"] as? Double)?.toInt() ?: 0
                                        val rate = (bucket["win_rate"] as? Double) ?: 0.0
                                        if (total < 50) {
                                            Text("📊 历史参考：样本积累中（${total}次），30天后可信",
                                                fontSize = 9.sp, color = FundTextSecondary, lineHeight = 13.sp)
                                        } else {
                                            Text("📊 同档位历史胜率 ${(rate * 100).toInt()}%（${total}次预测，${wins}次上涨）",
                                                fontSize = 9.sp, color = FundBlue, lineHeight = 13.sp)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))

                            // 关键因子已移除（普通用户不理解技术指标含义）
                        } ?: run {
                            // 无预测数据时显示净值
                            val est = estimate
                            val navValue = if (est != null && est.estimateNav > 0) est.estimateNav else f.nav
                            val changePct = if (est != null && est.estimateNav > 0) est.estimateChangePct else f.dayChange
                            Text("%.4f".format(navValue), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text(formatChange(changePct), fontSize = 14.sp,
                                color = changeColor(changePct), fontWeight = FontWeight.Medium)
                            Text("暂无AI预测数据", fontSize = 13.sp, color = FundTextSecondary)
                        }
                    }
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
                            // 最高最低 + 区间收益
                            val maxP = navHistory.maxByOrNull { it.nav }
                            val minP = navHistory.minByOrNull { it.nav }
                            val periodReturn = if (navHistory.first().nav > 0)
                                (navHistory.last().nav - navHistory.first().nav) / navHistory.first().nav * 100 else 0.0
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("最低: %.4f".format(minP?.nav), fontSize = 10.sp, color = FundGreen)
                                Text("区间收益: ${formatChange(periodReturn)}", fontSize = 10.sp,
                                    color = changeColor(periodReturn), fontWeight = FontWeight.Medium)
                                Text("最高: %.4f".format(maxP?.nav), fontSize = 10.sp, color = FundRed)
                            }
                        }
                    }
                }
            }

            // 风险收益分析（基于净值序列计算）— 放在 AI 预测面板之后
            item {
                if (navHistory.size >= 20) {
                    Spacer(Modifier.height(12.dp))
                    RiskReturnAnalysisCard(navHistory)
                }
            }

            // 基金信息
            detail?.let { d ->
                item {
                    Spacer(Modifier.height(12.dp))
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
                            if (d.stockRatio.isNotBlank()) InfoRow("股票占比", "${d.stockRatio}%")
                            if (d.bondRatio.isNotBlank() && d.bondRatio != "0") InfoRow("债券占比", "${d.bondRatio}%")
                            if (d.cashRatio.isNotBlank()) InfoRow("现金占比", "${d.cashRatio}%")
                            if (d.instHoldRatio.isNotBlank()) InfoRow("机构持有", "${d.instHoldRatio}%")
                        }
                    }
                }
            }

            // 费率信息
            overview?.let { ov ->
                item {
                    Spacer(Modifier.height(12.dp))
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
                        }
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
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    listOf("1000", "5000", "10000").forEach { quick ->
                        OutlinedButton(onClick = { amountText = quick },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("¥$quick", fontSize = 11.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    listOf("30000", "50000", "100000").forEach { quick ->
                        OutlinedButton(onClick = { amountText = quick },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("¥$quick", fontSize = 11.sp) }
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

/**
 * 风险收益分析卡片
 * 基于净值序列本地计算：夏普比率、最大回撤、年化波动率、卡玛比率、持有正收益概率
 */
@Composable
fun RiskReturnAnalysisCard(navHistory: List<NavPoint>) {
    if (navHistory.size < 20) return

    // 计算各项指标
    val metrics = remember(navHistory) { calculateRiskMetrics(navHistory) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("📈 风险收益分析", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("基于近${navHistory.size}个交易日数据计算", fontSize = 11.sp, color = FundTextSecondary)
            Spacer(Modifier.height(12.dp))

            // 风险控制
            Text("风险控制", fontSize = 12.sp, color = FundTextSecondary,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("最大回撤", "%.2f%%".format(Math.abs(metrics.maxDrawdown)), FundBlue)
                MetricItem("年化波动率", "%.2f%%".format(metrics.annualVolatility), FundBlue)
                MetricItem("净值新高率", "%.0f%%".format(metrics.newHighPct), FundBlue)
            }

            Spacer(Modifier.height(14.dp))
            // 性价比
            Text("性价比", fontSize = 12.sp, color = FundTextSecondary,
                fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MetricItem("夏普比率", "%.2f".format(metrics.sharpeRatio), FundBlue)
                MetricItem("卡玛比率", "%.2f".format(metrics.calmarRatio), FundBlue)
                MetricItem("正收益概率", "%.0f%%".format(metrics.positiveReturnPct), FundBlue)
            }


        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = valueColor)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = FundTextSecondary)
    }
}

/** 风险收益指标计算结果 */
data class RiskMetrics(
    val totalReturn: Double = 0.0,       // 区间总收益 %
    val annualReturn: Double = 0.0,      // 年化收益 %
    val maxDrawdown: Double = 0.0,       // 最大回撤 %（负数）
    val annualVolatility: Double = 0.0,  // 年化波动率 %
    val downsideRisk: Double = 0.0,      // 下行风险 %
    val sharpeRatio: Double = 0.0,       // 夏普比率
    val calmarRatio: Double = 0.0,       // 卡玛比率
    val positiveReturnPct: Double = 0.0, // 持有正收益概率 %
    val newHighPct: Double = 0.0         // 净值新高率 %
)

/** 从净值序列计算风险收益指标 */
fun calculateRiskMetrics(navHistory: List<NavPoint>): RiskMetrics {
    if (navHistory.size < 2) return RiskMetrics()

    val navs = navHistory.map { it.nav }
    val n = navs.size

    // 日收益率序列
    val dailyReturns = (1 until n).map { (navs[it] - navs[it - 1]) / navs[it - 1] }
    if (dailyReturns.isEmpty()) return RiskMetrics()

    // 区间总收益
    val totalReturn = (navs.last() - navs.first()) / navs.first() * 100

    // 年化收益（假设 252 个交易日/年）
    val tradingDays = dailyReturns.size
    val annualFactor = 252.0 / tradingDays
    val annualReturn = (Math.pow(1 + totalReturn / 100, annualFactor) - 1) * 100

    // 年化波动率
    val avgReturn = dailyReturns.average()
    val variance = dailyReturns.map { (it - avgReturn).let { d -> d * d } }.average()
    val dailyVol = Math.sqrt(variance)
    val annualVolatility = dailyVol * Math.sqrt(252.0) * 100

    // 下行风险（只计算负收益的波动）
    val negReturns = dailyReturns.filter { it < 0 }
    val downsideVariance = if (negReturns.isNotEmpty())
        negReturns.map { it * it }.average() else 0.0
    val downsideRisk = Math.sqrt(downsideVariance) * Math.sqrt(252.0) * 100

    // 最大回撤
    var maxDrawdown = 0.0
    var peak = navs[0]
    for (nav in navs) {
        if (nav > peak) peak = nav
        val drawdown = (nav - peak) / peak * 100
        if (drawdown < maxDrawdown) maxDrawdown = drawdown
    }

    // 夏普比率（无风险利率按年化 2% 计算）
    val riskFreeDaily = 0.02 / 252
    val excessReturns = dailyReturns.map { it - riskFreeDaily }
    val excessAvg = excessReturns.average()
    val excessVol = Math.sqrt(excessReturns.map { (it - excessAvg).let { d -> d * d } }.average())
    val sharpeRatio = if (excessVol > 0) excessAvg / excessVol * Math.sqrt(252.0) else 0.0

    // 卡玛比率（年化收益 / |最大回撤|）
    val calmarRatio = if (maxDrawdown != 0.0) annualReturn / Math.abs(maxDrawdown) else 0.0

    // 持有正收益概率（任意一天买入，持有到最后一天的正收益比例）
    val positiveCount = navs.indices.count { navs.last() > navs[it] }
    val positiveReturnPct = positiveCount.toDouble() / n * 100

    // 净值新高率（创新高的天数占比）
    var newHighCount = 0
    var runningMax = navs[0]
    for (nav in navs) {
        if (nav >= runningMax) {
            newHighCount++
            runningMax = nav
        }
    }
    val newHighPct = newHighCount.toDouble() / n * 100

    return RiskMetrics(
        totalReturn = totalReturn,
        annualReturn = annualReturn,
        maxDrawdown = maxDrawdown,
        annualVolatility = annualVolatility,
        downsideRisk = downsideRisk,
        sharpeRatio = sharpeRatio,
        calmarRatio = calmarRatio,
        positiveReturnPct = positiveReturnPct,
        newHighPct = newHighPct
    )
}
