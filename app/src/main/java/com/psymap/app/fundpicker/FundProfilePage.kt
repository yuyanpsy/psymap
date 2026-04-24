package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
            val cachedCount = vm.repo.getCachedFunds().size
            val favCount = vm.repo.getFavorites().size
            val posCount = vm.repo.getPositions().size
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ProfileStatItem("已缓存基金", "$cachedCount")
                    ProfileStatItem("自选关注", "$favCount")
                    ProfileStatItem("模拟持仓", "$posCount")
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
                    FundMenuItem(Icons.Default.TrendingUp, "风险偏好设置",
                        subtitle = vm.getRiskPreference()) { showRiskDialog = true }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    FundMenuItem(Icons.Default.Schedule, "默认时间维度",
                        subtitle = vm.getDefaultPeriod().label) { showPeriodDialog = true }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    FundMenuItem(Icons.Default.Notifications, "通知设置") { showNotificationSettings = true }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    FundMenuItem(Icons.Default.Science, "AI模型说明") { showAiModelInfo = true }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    FundMenuItem(Icons.Default.Refresh, "刷新数据") { vm.refresh() }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                    FundMenuItem(Icons.Default.Feedback, "意见反馈") { showFeedback = true }
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

    // AI模型说明
    if (showAiModelInfo) {
        AlertDialog(
            onDismissRequest = { showAiModelInfo = false },
            title = { Text("AI模型说明") },
            text = {
                Column {
                    Text("当前版本使用的预测模型：", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    ModelInfoItem("LSTM", "长短期记忆网络，基于历史净值时序数据预测走势")
                    ModelInfoItem("LightGBM", "梯度提升树，综合技术指标+基本面+资金流多因子预测")
                    ModelInfoItem("情绪分析", "基于财经新闻和政策文本的NLP情绪打分")
                    ModelInfoItem("集成模型", "加权融合以上三个模型的预测结果")
                    Spacer(Modifier.height(8.dp))
                    Text("⚠️ 当前AI评分基于历史涨跌数据的统计模型生成，" +
                            "后续将接入真实训练的深度学习模型。",
                        fontSize = 12.sp, color = Color(0xFFFF8F00), lineHeight = 18.sp)
                }
            },
            confirmButton = { TextButton(onClick = { showAiModelInfo = false }) { Text("关闭") } }
        )
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
