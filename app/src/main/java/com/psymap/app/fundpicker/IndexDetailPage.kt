package com.psymap.app.fundpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 将指数中文名映射到 API symbol */
fun indexNameToSymbol(name: String): String = when {
    name.contains("上证") -> "sh000001"
    name.contains("深证") -> "sz399001"
    name.contains("创业板") -> "sz399006"
    name.contains("黄金") -> "XAU"
    else -> "sh000001"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexDetailPage(
    indexName: String,
    currentValue: Double,
    currentChangePct: Double,
    onBack: () -> Unit
) {
    var kLine by remember { mutableStateOf<List<FundApi.IndexKPoint>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var periodDays by remember { mutableIntStateOf(30) }

    LaunchedEffect(indexName, periodDays) {
        loading = true
        val symbol = indexNameToSymbol(indexName)
        FundApi.fetchIndexKLine(symbol, periodDays,
            onResult = { pts ->
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    kLine = pts
                    loading = false
                }
            },
            onError = {
                android.os.Handler(android.os.Looper.getMainLooper()).post { loading = false }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(indexName, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            // 当前值 + 涨跌幅 + 周期区间收益
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("当前点位", fontSize = 12.sp, color = FundTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        val valueStr = if (indexName.contains("黄金") || currentValue < 1000)
                            "%.2f".format(currentValue) else "%.2f".format(currentValue)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(valueStr, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(12.dp))
                            Text(formatChange(currentChangePct), fontSize = 18.sp,
                                color = changeColor(currentChangePct),
                                fontWeight = FontWeight.Medium)
                        }

                        if (kLine.size >= 2) {
                            val first = kLine.first().close
                            val last = kLine.last().close
                            val periodReturn = if (first > 0) (last - first) / first * 100 else 0.0
                            val maxP = kLine.maxByOrNull { it.high }?.high ?: 0.0
                            val minP = kLine.minByOrNull { it.low }?.low ?: 0.0
                            Spacer(Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                IndexStatCell("区间收益", formatChange(periodReturn),
                                    changeColor(periodReturn))
                                IndexStatCell("区间最高", "%.2f".format(maxP), FundRed)
                                IndexStatCell("区间最低", "%.2f".format(minP), FundGreen)
                            }
                        }
                    }
                }
            }

            // 周期选择
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(7, 30, 90, 180, 365).forEach { d ->
                        val label = when (d) {
                            7 -> "7天"; 30 -> "30天"; 90 -> "3月"
                            180 -> "6月"; 365 -> "1年"; else -> "${d}天"
                        }
                        val selected = periodDays == d
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (selected) FundBlue else Color(0xFFE8EAED))
                                .clickable { periodDays = d }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 12.sp,
                                color = if (selected) Color.White else FundTextSecondary)
                        }
                    }
                }
            }

            // K线图
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("走势图", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        if (loading) {
                            Box(modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = FundBlue,
                                    modifier = Modifier.size(28.dp))
                            }
                        } else if (kLine.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(220.dp),
                                contentAlignment = Alignment.Center) {
                                Text("暂无数据", fontSize = 13.sp, color = FundTextSecondary)
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(kLine.first().date, fontSize = 10.sp, color = FundTextSecondary)
                                Text("${kLine.size}个交易日", fontSize = 10.sp, color = FundTextSecondary)
                                Text(kLine.last().date, fontSize = 10.sp, color = FundTextSecondary)
                            }
                            Spacer(Modifier.height(4.dp))
                            IndexKLineChart(kLine, modifier = Modifier.fillMaxWidth().height(220.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexStatCell(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = FundTextSecondary)
    }
}

/** 指数K线图（蜡烛图 + 折线） */
@Composable
fun IndexKLineChart(data: List<FundApi.IndexKPoint>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val maxP = data.maxOf { it.high }
    val minP = data.minOf { it.low }
    val range = (maxP - minP).coerceAtLeast(0.0001)

    Canvas(modifier = modifier
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFFF8FAFF))
    ) {
        val w = size.width
        val h = size.height
        val padding = 8.dp.toPx()

        // 网格线
        for (i in 0..3) {
            val y = padding + (h - 2 * padding) * i / 3
            drawLine(Color(0xFFE8EAED), Offset(0f, y), Offset(w, y), strokeWidth = 0.5.dp.toPx())
        }

        // 折线图（收盘价连线）
        val path = Path()
        val step = w / (data.size - 1).coerceAtLeast(1)
        data.forEachIndexed { i, p ->
            val x = i * step
            val y = padding + (h - 2 * padding) * (1 - ((p.close - minP) / range)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, FundBlue, style = Stroke(width = 2.dp.toPx()))

        // 起点/终点圆点
        if (data.isNotEmpty()) {
            val firstY = padding + (h - 2 * padding) * (1 - ((data.first().close - minP) / range)).toFloat()
            drawCircle(FundBlue, 3.dp.toPx(), Offset(0f, firstY))
            val lastY = padding + (h - 2 * padding) * (1 - ((data.last().close - minP) / range)).toFloat()
            drawCircle(FundBlue, 4.dp.toPx(), Offset(w, lastY))
        }
    }
}
