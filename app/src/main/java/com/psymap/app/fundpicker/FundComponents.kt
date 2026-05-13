package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 配色常量 — 现代金融风格
val FundBlue = Color(0xFF2962FF)       // 主色调（深蓝）
val FundRed = Color(0xFFE53935)        // 上涨（中国市场红涨）
val FundGreen = Color(0xFF00C853)      // 下跌（饱和翠绿，和热力图一致）
val FundGold = Color(0xFFD4AF37)       // 金色（高AI预测）
val FundBg = Color(0xFFF5F7FA)         // 页面背景（浅灰蓝）
val FundCardBg = Color.White           // 卡片背景
val FundTextPrimary = Color(0xFF1A1A2E)// 主文字（深色）
val FundTextSecondary = Color(0xFF8E99A4) // 次要文字

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
    isFavorite: Boolean = false,
    isPositioned: Boolean = false,
    confidence: Int = 0,
    sharpe: Double = 0.0,
    maxDrawdown: Double = 100.0,
    positivePct: Double = 0.0,
    onFavoriteToggle: () -> Unit = {},
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
                // 统一头部：【板块】【基金名称】【基金代码】【持仓】【⭐】
                FundHeaderRow(
                    fundName = fund.name, fundCode = fund.code,
                    isFavorite = isFavorite, isPositioned = isPositioned,
                    onFavoriteToggle = onFavoriteToggle,
                    goldenWhenHighAi = true, aiScore = fund.aiScore,
                    confidence = confidence,
                    sharpe = sharpe, maxDrawdown = maxDrawdown, positivePct = positivePct
                )
                Spacer(Modifier.height(6.dp))
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

/** 置信度星星 — 用 Material Icon 填充星 */
@Composable
fun ConfidenceStars(level: Int) {
    Row {
        repeat(5) { i ->
            Icon(
                imageVector = if (i < level) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (i < level) Color(0xFFFFB300) else Color(0xFFCCCCCC),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 收藏/持仓徽章 - 统一样式 */
@Composable
fun FundBadges(
    isFavorite: Boolean,
    isPositioned: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isFavorite && !isPositioned) return
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (isFavorite) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFFFF4CC))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text("⭐自选", fontSize = 9.sp, color = Color(0xFFB8860B),
                    fontWeight = FontWeight.Medium)
            }
        }
        if (isPositioned) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE3F2FD))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text("持仓", fontSize = 9.sp, color = FundBlue,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * 统一的基金头部行：【板块】【基金名称】【基金代码】【持仓标签】【可点击⭐️】
 * 扁平结构，名称占满中间，避免嵌套 Row + weight(fill=false) 导致的测量错乱
 */
@Composable
fun FundHeaderRow(
    fundName: String,
    fundCode: String,
    isFavorite: Boolean,
    isPositioned: Boolean,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    nameFontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    goldenWhenHighAi: Boolean = false,
    aiScore: Int = 0,
    confidence: Int = 0,
    sharpe: Double = 0.0,
    maxDrawdown: Double = 100.0,
    positivePct: Double = 0.0
) {
    val sector = remember(fundName) { FundApi.classifyFundSector(fundName) }
    // 金色条件：AI≥70 + 置信度≥4 + 夏普>2 + 回撤<15% + 正收益>80%
    val isGolden = goldenWhenHighAi && aiScore >= 70 && confidence >= 4
            && sharpe > 2.0 && maxDrawdown < 15.0 && positivePct > 80.0
    val nameColor = if (isGolden) Color(0xFFD4AF37) else FundTextPrimary
    // 右侧状态区宽度：持仓标签(≈36dp) + 间距(4dp) + ⭐(24dp) = ~66dp，保留则为固定
    val hasPositionTag = isPositioned
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 【板块标签】固定宽度 38dp，无板块时保留空间
        Box(
            modifier = Modifier
                .width(38.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (sector.isNotBlank()) Color(0xFFF0F4FF) else Color.Transparent)
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            if (sector.isNotBlank()) {
                Text(sector, fontSize = 10.sp, color = FundBlue,
                    fontWeight = FontWeight.Medium, maxLines = 1)
            }
        }
        Spacer(Modifier.width(6.dp))

        // 【基金名称】占据剩余空间，超长用省略号
        Text(
            fundName,
            fontSize = nameFontSize, fontWeight = FontWeight.Bold,
            color = nameColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        // 【基金代码】固定
        Text(fundCode, fontSize = 11.sp, color = FundTextSecondary, maxLines = 1)

        Spacer(Modifier.width(8.dp))

        // 【持仓标签】
        if (hasPositionTag) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE3F2FD))
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text("持仓", fontSize = 10.sp, color = FundBlue,
                    fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Spacer(Modifier.width(4.dp))
        }
        // 【⭐ 可点击】用 Material Icon 替代 emoji，避免显示不全
        IconButton(
            onClick = { onFavoriteToggle() },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = "收藏",
                tint = if (isFavorite) Color(0xFFFFB300) else FundTextSecondary,
                modifier = Modifier.size(20.dp)
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
