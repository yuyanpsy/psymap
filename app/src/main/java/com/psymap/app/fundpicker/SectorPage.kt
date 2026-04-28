package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

// ==================== 行业布局热力图（TreeMap） ====================

/**
 * 行业板块热力图组件
 * 参考截图中的 TreeMap 布局：大小代表资金量/涨幅，颜色代表涨跌
 */
@Composable
fun SectorHeatmapSection(
    vm: FundPickerViewModel,
    onSectorClick: (SectorDetail) -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sectorDetails by vm.sectorDetails.collectAsState()
    val sortType by vm.sectorSortType.collectAsState()
    val timePeriod by vm.sectorTimePeriod.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 标题行
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("行业布局", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("重仓某板块", fontSize = 12.sp, color = FundTextSecondary)
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.clickable { onMoreClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("更多", fontSize = 13.sp, color = FundTextSecondary)
                    Icon(Icons.Default.ArrowForward, contentDescription = null,
                        modifier = Modifier.size(14.dp), tint = FundTextSecondary)
                }
            }
            Spacer(Modifier.height(12.dp))

            // 排序 + 时间维度选择
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 涨幅/资金切换
                SectorSortType.entries.forEach { type ->
                    val selected = type == sortType
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) Color(0xFFF5F5F5) else Color.Transparent)
                            .clickable { vm.setSectorSortType(type) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(type.label, fontSize = 13.sp,
                            color = if (selected) FundTextPrimary else FundTextSecondary,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                    }
                }
                Spacer(Modifier.weight(1f))
                // 时间维度
                SectorTimePeriod.entries.forEach { period ->
                    val selected = period == timePeriod
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) Color(0xFFF5F5F5) else Color.Transparent)
                            .clickable { vm.setSectorTimePeriod(period) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(period.label, fontSize = 13.sp,
                            color = if (selected) FundTextPrimary else FundTextSecondary,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // TreeMap 热力图
            if (sectorDetails.isNotEmpty()) {
                SectorTreeMap(
                    sectors = sectorDetails.take(12),
                    sortType = sortType,
                    onClick = onSectorClick
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FundBlue, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}


/**
 * TreeMap 热力图布局
 * 使用 squarified treemap 算法的简化版本
 * 每个块的大小基于涨幅绝对值或资金流入量
 */
@Composable
fun SectorTreeMap(
    sectors: List<SectorDetail>,
    sortType: SectorSortType,
    onClick: (SectorDetail) -> Unit
) {
    if (sectors.isEmpty()) return

    // 计算权重
    val weighted = sectors.mapIndexed { index, s ->
        val weight = when (sortType) {
            SectorSortType.CHANGE -> abs(s.changePct).coerceAtLeast(0.5)
            SectorSortType.CAPITAL -> abs(s.mainNetInflow).coerceAtLeast(0.5)
        }
        // 前几个权重更大
        val boost = when (index) {
            0 -> 3.0; 1 -> 2.2; 2 -> 1.8; 3 -> 1.5; else -> 1.0
        }
        s to (weight * boost)
    }
    val totalWeight = weighted.sumOf { it.second }

    // 使用自定义 Layout 实现 TreeMap
    Layout(
        content = {
            weighted.forEach { (sector, weight) ->
                SectorTreeMapCell(
                    sector = sector,
                    sortType = sortType,
                    onClick = { onClick(sector) }
                )
            }
        },
        modifier = Modifier.fillMaxWidth().height(280.dp)
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val gap = 3 // px gap between cells

        // 简化的 TreeMap 布局算法
        data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)
        val rects = mutableListOf<Rect>()

        // 使用 slice-and-dice 算法
        fun layoutRow(items: List<Pair<SectorDetail, Double>>, x: Int, y: Int, w: Int, h: Int, horizontal: Boolean) {
            if (items.isEmpty()) return
            val rowTotal = items.sumOf { it.second }
            var offset = 0
            items.forEachIndexed { i, (_, wt) ->
                val ratio = wt / rowTotal
                if (horizontal) {
                    val cellW = if (i == items.lastIndex) w - offset else (w * ratio).roundToInt()
                    rects.add(Rect(x + offset, y, (cellW - gap).coerceAtLeast(1), (h - gap).coerceAtLeast(1)))
                    offset += cellW
                } else {
                    val cellH = if (i == items.lastIndex) h - offset else (h * ratio).roundToInt()
                    rects.add(Rect(x, y + offset, (w - gap).coerceAtLeast(1), (cellH - gap).coerceAtLeast(1)))
                    offset += cellH
                }
            }
        }

        // 分成两行：前4个占上半部分，后面的占下半部分
        val topItems = weighted.take(4)
        val bottomItems = weighted.drop(4)
        val topWeight = topItems.sumOf { it.second }
        val bottomWeight = bottomItems.sumOf { it.second }
        val topRatio = topWeight / (topWeight + bottomWeight)
        val topH = (height * topRatio).roundToInt().coerceIn(height / 3, height * 2 / 3)
        val bottomH = height - topH

        // 上半部分：第一个大块 + 右侧3个
        if (topItems.isNotEmpty()) {
            val firstWeight = topItems.first().second
            val restWeight = topItems.drop(1).sumOf { it.second }
            val firstRatio = firstWeight / (firstWeight + restWeight)
            val firstW = (width * firstRatio).roundToInt().coerceIn(width / 3, width * 2 / 3)

            // 第一个大块
            rects.add(Rect(0, 0, (firstW - gap).coerceAtLeast(1), (topH - gap).coerceAtLeast(1)))
            // 右侧竖排
            layoutRow(topItems.drop(1), firstW, 0, width - firstW, topH, horizontal = false)
        }

        // 下半部分
        if (bottomItems.isNotEmpty()) {
            // 分成两行
            val midItems = bottomItems.take(4)
            val lastItems = bottomItems.drop(4)
            if (lastItems.isEmpty()) {
                layoutRow(midItems, 0, topH, width, bottomH, horizontal = true)
            } else {
                val midH = bottomH * 2 / 3
                layoutRow(midItems, 0, topH, width, midH, horizontal = true)
                layoutRow(lastItems, 0, topH + midH, width, bottomH - midH, horizontal = true)
            }
        }

        // 测量和放置
        val placeables = measurables.mapIndexed { i, measurable ->
            val rect = rects.getOrElse(i) { Rect(0, 0, 50, 50) }
            measurable.measure(Constraints.fixed(rect.w.coerceAtLeast(1), rect.h.coerceAtLeast(1)))
        }

        layout(width, height) {
            placeables.forEachIndexed { i, placeable ->
                val rect = rects.getOrElse(i) { Rect(0, 0, 0, 0) }
                placeable.placeRelative(rect.x, rect.y)
            }
        }
    }
}

@Composable
private fun SectorTreeMapCell(
    sector: SectorDetail,
    sortType: SectorSortType,
    onClick: () -> Unit
) {
    // 颜色：涨红跌绿，深浅根据幅度
    val bgColor = when {
        sector.changePct > 5 -> Color(0xFFCC0000)
        sector.changePct > 3 -> Color(0xFFDD2222)
        sector.changePct > 1 -> Color(0xFFEE4444)
        sector.changePct > 0 -> Color(0xFFEE6666)
        sector.changePct > -1 -> Color(0xFF66AA66)
        sector.changePct > -3 -> Color(0xFF44AA44)
        sector.changePct > -5 -> Color(0xFF228822)
        else -> Color(0xFF006600)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(sector.name, fontSize = 12.sp, color = Color.White,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center)
            if (sortType == SectorSortType.CAPITAL) {
                Text("%.2f亿".format(sector.mainNetInflow), fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
            }
            Text(formatChange(sector.changePct), fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
        }
    }
}
