package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun FundProfilePage(
    vm: FundPickerViewModel = viewModel(),
    onBack: () -> Unit
) {
    var showRiskDialog by remember { mutableStateOf(false) }
    var showPeriodDialog by remember { mutableStateOf(false) }
    var showAiModelInfo by remember { mutableStateOf(false) }
    var showNotificationSettings by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(FundBg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Text("我的", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 12.dp))
        }

        // 数据状态卡片
        item {
            // 使用 StateFlow，和各 tab 页完全一致
            val favorites by vm.favorites.collectAsState()
            val positions by vm.positions.collectAsState()
            val predictions by vm.aiPredictions.collectAsState()
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStatItem("AI预测基金", "${predictions.size}")
                    ProfileStatItem("自选关注", "${favorites.size}")
                    ProfileStatItem("模拟持仓", "${positions.size}")
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // 设置列表
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column {
                    FundMenuItem(Icons.Default.Science, "AI预测算法原理") { showAiModelInfo = true }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    FundMenuItem(Icons.Default.Refresh, "刷新数据") { vm.refresh() }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    FundMenuItem(Icons.Default.ExitToApp, "返回羽言心理", onClick = onBack)
                }
            }
        }

        // 免责声明
        item {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚠️ 免责声明", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "本App所有AI预测结果仅供参考，不构成任何投资建议。" +
                                "基金投资有风险，入市需谨慎。过往业绩不代表未来表现。" +
                                "\n\n数据来源：东方财富/天天基金（公开数据）",
                        fontSize = 13.sp, color = FundTextSecondary, lineHeight = 20.sp
                    )
                }
            }
        }
    }

    // ==================== 弹窗 ====================

    // 风险偏好设置
    if (showRiskDialog) {
        val options = listOf("保守型", "稳健型", "进取型")
        var selected by remember { mutableStateOf(vm.getRiskPreference()) }
        AlertDialog(
            onDismissRequest = { showRiskDialog = false },
            title = { Text("风险偏好设置") },
            text = {
                Column {
                    Text("选择你的投资风格，AI推荐将据此调整", fontSize = 13.sp, color = FundTextSecondary)
                    Spacer(Modifier.height(12.dp))
                    options.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = option }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == option, onClick = { selected = option })
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(option, fontSize = 15.sp)
                                Text(
                                    when (option) {
                                        "保守型" -> "偏好债券型/货币型，追求稳定收益"
                                        "稳健型" -> "偏好混合型，平衡风险与收益"
                                        else -> "偏好股票型/指数型，追求高收益"
                                    },
                                    fontSize = 12.sp, color = FundTextSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.setRiskPreference(selected); showRiskDialog = false }) {
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = { showRiskDialog = false }) { Text("取消") } }
        )
    }

    // 默认时间维度
    if (showPeriodDialog) {
        val periods = listOf(TimePeriod.D7, TimePeriod.D30, TimePeriod.M3,
            TimePeriod.M6, TimePeriod.Y1, TimePeriod.Y3)
        var selected by remember { mutableStateOf(vm.getDefaultPeriod()) }
        AlertDialog(
            onDismissRequest = { showPeriodDialog = false },
            title = { Text("默认时间维度") },
            text = {
                Column {
                    Text("设置净值走势、业绩对比的默认时间范围", fontSize = 13.sp, color = FundTextSecondary)
                    Spacer(Modifier.height(12.dp))
                    periods.forEach { period ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = period }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selected == period, onClick = { selected = period })
                            Spacer(Modifier.width(8.dp))
                            Text(period.label, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { vm.setDefaultPeriod(selected); showPeriodDialog = false }) {
                    Text("保存")
                }
            },
            dismissButton = { TextButton(onClick = { showPeriodDialog = false }) { Text("取消") } }
        )
    }

    // 通知设置
    if (showNotificationSettings) {
        AlertDialog(
            onDismissRequest = { showNotificationSettings = false },
            title = { Text("通知设置") },
            text = {
                Column {
                    Text("AI预警通知功能将在后续版本中上线", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("计划功能：", fontSize = 13.sp, color = FundTextSecondary)
                    Text("· 自选基金AI评分大幅变动时推送", fontSize = 13.sp, color = FundTextSecondary)
                    Text("· 持仓基金净值异常波动提醒", fontSize = 13.sp, color = FundTextSecondary)
                    Text("· 每日收盘后AI分析报告", fontSize = 13.sp, color = FundTextSecondary)
                }
            },
            confirmButton = { TextButton(onClick = { showNotificationSettings = false }) { Text("知道了") } }
        )
    }

    // AI模型说明 — 全屏页面
    if (showAiModelInfo) {
        AiModelInfoPage(onBack = { showAiModelInfo = false })
    }

    // 意见反馈
    if (showFeedback) {
        var feedbackText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFeedback = false },
            title = { Text("意见反馈") },
            text = {
                Column {
                    Text("你的反馈对我们很重要", fontSize = 13.sp, color = FundTextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedbackText,
                        onValueChange = { feedbackText = it },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        placeholder = { Text("请输入你的建议或问题...") },
                        maxLines = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFeedback = false },
                    enabled = feedbackText.isNotBlank()
                ) { Text("提交") }
            },
            dismissButton = { TextButton(onClick = { showFeedback = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun ProfileStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = FundBlue)
        Text(label, fontSize = 12.sp, color = FundTextSecondary)
    }
}

@Composable
private fun ModelInfoItem(name: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FundBlue)
        Text(desc, fontSize = 12.sp, color = FundTextSecondary, lineHeight = 16.sp)
    }
}

@Composable
private fun FundMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = FundTextSecondary,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (subtitle.isNotBlank()) {
            Text(subtitle, fontSize = 13.sp, color = FundTextSecondary)
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            tint = Color(0xFFCCCCCC), modifier = Modifier.size(20.dp))
    }
}

/** AI 预测算法原理 — 全屏页面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiModelInfoPage(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI预测算法原理", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(FundBg),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                SectionCard("预测目标") {
                    Text("预测每只基金未来30天净值上涨的概率（0-100%）", fontSize = 13.sp, color = FundTextSecondary)
                }
            }
            item {
                SectionCard("模型架构") {
                    Text("• GradientBoosting（权重60%）\n• RandomForest（权重40%）\n• 集成概率 = GB×0.6 + RF×0.4\n• 两模型越一致，置信度越高（5⭐=完全一致）",
                        fontSize = 13.sp, color = FundTextSecondary, lineHeight = 20.sp)
                }
            }
            item {
                SectionCard("输入特征（38个技术指标）") {
                    Text("• 动量指标（5/10/20/60日）— 趋势延续性\n• RSI(6/14/28) — 超买超卖识别\n• MACD及柱状图 — 趋势跟踪\n• 布林带位置与宽度 — 均值回归\n• 波动率（5/10/20/60日）— 变盘预警\n• 趋势一致性 — 多周期方向确认\n• 均线偏离度 — 趋势强度\n• 最大回撤（20/60日）— 风险度量",
                        fontSize = 13.sp, color = FundTextSecondary, lineHeight = 20.sp)
                }
            }
            item {
                SectionCard("风险收益指标") {
                    InfoItem("夏普比率", "每承担1单位风险获得多少超额收益", ">2 优秀 | 1.5-2 良好 | <1 差")
                    InfoItem("最大回撤", "历史最高点到最低点的最大跌幅", "<10% 优秀 | 10-15% 良好 | >30% 高风险")
                    InfoItem("年化波动率", "收益的不确定性程度", "<15% 低 | 15-25% 中 | >25% 高")
                    InfoItem("卡玛比率", "年化收益 / 最大回撤", ">2 优秀 | 1-2 良好 | <1 差")
                    InfoItem("正收益概率", "任意一天买入持有到今天的赚钱比例", ">80% 优秀 | 60-80% 良好 | <50% 多数人亏")
                    InfoItem("净值新高率", "创历史新高天数占比", ">30% 强势 | <15% 弱势")
                }
            }
            item {
                SectionCard("综合购买策略") {
                    Text("强烈推荐（金色标注）", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFD4AF37))
                    Text("• AI预测 ≥70% + 置信度 ≥4\n• 夏普比率 >2\n• 最大回撤 <15%\n• 正收益概率 >80%",
                        fontSize = 13.sp, color = FundTextSecondary, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("可以考虑", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FundBlue)
                    Text("• AI预测 60-70%\n• 夏普比率 >1.0\n• 最大回撤 <20%\n• 卡玛比率 >1",
                        fontSize = 13.sp, color = FundTextSecondary, lineHeight = 20.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("建议回避", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = FundTextSecondary)
                    Text("• AI预测 <50%\n• 夏普比率 <0.5\n• 最大回撤 >30%\n• 正收益概率 <40%",
                        fontSize = 13.sp, color = FundTextSecondary, lineHeight = 20.sp)
                }
            }
            item {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun InfoItem(name: String, desc: String, reference: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = FundBlue)
        Text(desc, fontSize = 12.sp, color = FundTextSecondary)
        Text("参考：$reference", fontSize = 11.sp, color = FundTextSecondary)
    }
}
