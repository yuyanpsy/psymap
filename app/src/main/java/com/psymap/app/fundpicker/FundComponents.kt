package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 配色常量
val FundBlue = Color(0xFF1A73E8)
val FundRed = Color(0xFFEA4335)    // 上涨（中国市场红涨）
val FundGreen = Color(0xFF34A853)  // 下跌
val FundBg = Color(0xFFF8F9FA)
val FundCardBg = Color.White
val FundTextPrimary = Color(0xFF202124)
val FundTextSecondary = Color(0xFF5F6368)

fun changeColor(value: Double): Color = when {
    value > 0 -> FundRed
    value < 0 -> FundGreen
    else -> FundTextSecondary
}

fun formatChange(value: Double): String = when {
    value > 0 -> "+%.2f%%".format(value)
    else -> "%.2f%%".format(value)
}

fun formatMoney(value: Double): String = when {
    value >= 10000 -> "%.2f万".format(value / 10000)
    else -> "%.2f".format(value)
}

/** 时间维度选择器 */
@Composable
fun TimePeriodSelector(
    periods: List<TimePeriod> = TimePeriod.entries,
    selected: TimePeriod,
    onSelect: (TimePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        periods.forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) FundBlue else Color(0xFFE8EAED))
                    .clickable { onSelect(period) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    period.label,
                    fontSize = 12.sp,
                    color = if (isSelected) Color.White else FundTextSecondary
                )
            }
        }
    }
}

/** 基金列表卡片 */
@Composable
fun FundListItem(
    fund: Fund,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = FundCardBg),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fund.name, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    Text(fund.code, fontSize = 12.sp, color = FundTextSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${fund.type} · ${fund.riskLevel}风险",
                        fontSize = 12.sp, color = FundTextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("净值 %.4f".format(fund.nav), fontSize = 13.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(formatChange(fund.dayChange),
                        fontSize = 13.sp, color = changeColor(fund.dayChange),
                        fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(12.dp))
                    Text("AI ${fund.aiScore}%", fontSize = 13.sp,
                        color = FundBlue, fontWeight = FontWeight.Medium)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/** AI评分进度条 */
@Composable
fun AiScoreBar(score: Int, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI预测上涨概率", fontSize = 12.sp, color = FundTextSecondary)
            Spacer(Modifier.weight(1f))
            Text("$score%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FundBlue)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = FundBlue,
            trackColor = Color(0xFFE8EAED),
            gapSize = 0.dp,
            drawStopIndicator = {}
        )
    }
}

/** 置信度星星 */
@Composable
fun ConfidenceStars(level: Int) {
    Row {
        repeat(5) { i ->
            Text(
                if (i < level) "⭐" else "☆",
                fontSize = 14.sp
            )
        }
    }
}

/** 涨跌幅标签 */
@Composable
fun ChangeTag(value: Double, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatChange(value), fontSize = 14.sp,
            fontWeight = FontWeight.Medium, color = changeColor(value))
        Text(label, fontSize = 11.sp, color = FundTextSecondary)
    }
}
