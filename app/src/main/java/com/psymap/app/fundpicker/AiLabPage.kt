package com.psymap.app.fundpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLabPage(vm: FundPickerViewModel, onBack: () -> Unit) {
    val fund by vm.selectedFund.collectAsState()
    val compareResults by vm.compareResults.collectAsState()
    val backtestResults by vm.backtestResults.collectAsState()
    val f = fund ?: return

    var selectedPeriod by remember { mutableStateOf(TimePeriod.D30) }
    var lstmWindow by remember { mutableFloatStateOf(60f) }
    var weightLstm by remember { mutableFloatStateOf(0.3f) }
    var weightLgbm by remember { mutableFloatStateOf(0.3f) }
    var weightSentiment by remember { mutableFloatStateOf(0.15f) }
    var weightTransformer by remember { mutableFloatStateOf(0.15f) }
    var weightArima by remember { mutableFloatStateOf(0.1f) }
    var backtestPeriod by remember { mutableStateOf(TimePeriod.Y1) }

    val models = remember { mutableStateListOf("LSTM", "LightGBM", "Transformer", "集成模型") }
    val allModels = listOf("LSTM", "GRU", "Transformer", "LightGBM", "XGBoost", "ARIMA", "纯技术面", "集成模型")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI实验室", fontSize = 16.sp) },
                navigationIcon = { IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).background(FundBg),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text("${f.name}  ${f.code}", fontSize = 14.sp, color = FundTextSecondary,
                    modifier = Modifier.padding(16.dp))
            }

            // 算法选择 + 说明
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("算法选择", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("勾选要参与对比的预测模型", fontSize = 12.sp, color = FundTextSecondary)
                        Spacer(Modifier.height(4.dp))
                        allModels.forEach { model ->
                            Row(verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)) {
                                Checkbox(checked = model in models,
                                    onCheckedChange = { if (it) models.add(model) else models.remove(model) })
                                Column {
                                    Text(model, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(modelDescription(model), fontSize = 11.sp,
                                        color = FundTextSecondary, lineHeight = 14.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 预测周期
            item {
                Spacer(Modifier.height(12.dp))
                SectionCard("预测周期", "模型预测未来多长时间的涨跌概率") {
                    TimePeriodSelector(
                        periods = listOf(TimePeriod.D7, TimePeriod.D30, TimePeriod.M3, TimePeriod.M6, TimePeriod.Y1),
                        selected = selectedPeriod, onSelect = { selectedPeriod = it }
                    )
                }
            }

            // 参数调节
            item {
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("参数调节", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("调整模型超参数，观察对预测结果的影响", fontSize = 12.sp, color = FundTextSecondary)

                        Spacer(Modifier.height(16.dp))
                        ParamSlider("LSTM 回看窗口", "模型参考过去多少个交易日的数据。窗口越大，模型越关注长期趋势；窗口越小，对近期变化更敏感。",
                            value = lstmWindow, range = 20f..180f, steps = 15,
                            valueLabel = "${lstmWindow.toInt()}日", color = FundBlue,
                            onValueChange = { lstmWindow = it })

                        ParamSlider("集成权重 — LSTM", "LSTM在最终集成预测中的权重占比。LSTM擅长捕捉时序趋势。",
                            value = weightLstm, range = 0f..1f,
                            valueLabel = "%.0f%%".format(weightLstm * 100), color = FundBlue,
                            onValueChange = { weightLstm = it })

                        ParamSlider("集成权重 — LightGBM", "LightGBM在集成中的权重。擅长多因子综合判断（技术指标+基本面+资金流）。",
                            value = weightLgbm, range = 0f..1f,
                            valueLabel = "%.0f%%".format(weightLgbm * 100), color = Color(0xFFFF9800),
                            onValueChange = { weightLgbm = it })

                        ParamSlider("集成权重 — Transformer", "Transformer在集成中的权重。自注意力机制擅长捕捉长距离依赖关系。",
                            value = weightTransformer, range = 0f..1f,
                            valueLabel = "%.0f%%".format(weightTransformer * 100), color = Color(0xFF9C27B0),
                            onValueChange = { weightTransformer = it })

                        ParamSlider("集成权重 — 情绪分析", "NLP情绪模型的权重。基于财经新闻和政策文本分析市场情绪。",
                            value = weightSentiment, range = 0f..1f,
                            valueLabel = "%.0f%%".format(weightSentiment * 100), color = Color(0xFF4CAF50),
                            onValueChange = { weightSentiment = it })

                        ParamSlider("集成权重 — ARIMA", "经典统计模型的权重。适合平稳市场，对突发事件不敏感。",
                            value = weightArima, range = 0f..1f,
                            valueLabel = "%.0f%%".format(weightArima * 100), color = Color(0xFF795548),
                            onValueChange = { weightArima = it })

                        val total = weightLstm + weightLgbm + weightSentiment + weightTransformer + weightArima
                        if (total > 0) {
                            Spacer(Modifier.height(4.dp))
                            Text("归一化权重: LSTM %.0f%% / LightGBM %.0f%% / Transformer %.0f%% / 情绪 %.0f%% / ARIMA %.0f%%".format(
                                weightLstm / total * 100, weightLgbm / total * 100,
                                weightTransformer / total * 100, weightSentiment / total * 100,
                                weightArima / total * 100),
                                fontSize = 11.sp, color = FundTextSecondary)
                        }
                    }
                }
            }

            // 运行按钮
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        vm.runModelCompare(f.code, selectedPeriod, lstmWindow.toInt(),
                            weightLstm, weightLgbm, weightSentiment, weightTransformer, weightArima)
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FundBlue)
                ) { Text("🚀 运行对比分析", fontSize = 15.sp) }
            }

            // 对比结果
            if (compareResults.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("对比结果", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("各模型对该基金未来${selectedPeriod.label}的预测", fontSize = 12.sp, color = FundTextSecondary)
                            Spacer(Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("模型", fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text("上涨概率", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                                Text("置信度", fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            compareResults.filter { it.modelName in models }.forEach { result ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Text(result.modelName, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                        Text("${result.probability}%", fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium, color = FundBlue,
                                            modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                                        Row(modifier = Modifier.width(72.dp),
                                            horizontalArrangement = Arrangement.Center) {
                                            ConfidenceStars(result.confidence)
                                        }
                                    }
                                    if (result.description.isNotBlank()) {
                                        Text(result.description, fontSize = 10.sp,
                                            color = FundTextSecondary, lineHeight = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // 回测结果
                item {
                    Spacer(Modifier.height(12.dp))
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("📈 历史回测", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text("用历史数据模拟各模型的预测表现", fontSize = 12.sp, color = FundTextSecondary)
                            Spacer(Modifier.height(8.dp))
                            TimePeriodSelector(
                                periods = listOf(TimePeriod.M3, TimePeriod.M6, TimePeriod.Y1, TimePeriod.Y3),
                                selected = backtestPeriod, onSelect = { backtestPeriod = it }
                            )
                            Spacer(Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("模型", fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                Text("命中率", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(50.dp), textAlign = TextAlign.Center)
                                Text("年化", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(50.dp), textAlign = TextAlign.Center)
                                Text("回撤", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(46.dp), textAlign = TextAlign.Center)
                                Text("夏普", fontSize = 11.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                            backtestResults.forEach { bt ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Text(bt.modelName, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                    Text("%.0f%%".format(bt.hitRate * 100), fontSize = 12.sp,
                                        modifier = Modifier.width(50.dp), textAlign = TextAlign.Center)
                                    Text("+%.1f%%".format(bt.annualReturn), fontSize = 12.sp,
                                        color = FundRed, modifier = Modifier.width(50.dp), textAlign = TextAlign.Center)
                                    Text("%.1f%%".format(bt.maxDrawdown), fontSize = 12.sp,
                                        color = FundGreen, modifier = Modifier.width(46.dp), textAlign = TextAlign.Center)
                                    Text("%.2f".format(bt.sharpeRatio), fontSize = 12.sp,
                                        modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Text("指标说明:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text("· 命中率: 模型预测涨跌方向的正确比例\n" +
                                    "· 年化收益: 按模型信号操作的年化回报率\n" +
                                    "· 最大回撤: 期间最大亏损幅度（越小越好）\n" +
                                    "· 夏普比率: 风险调整后收益（>1为优秀）",
                                fontSize = 11.sp, color = FundTextSecondary, lineHeight = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("⚠️ 回测结果不代表未来表现", fontSize = 11.sp, color = Color(0xFFFF8F00))
                        }
                    }
                }
            }
        }
    }
}

private fun modelDescription(name: String): String = when (name) {
    "LSTM" -> "长短期记忆网络，擅长捕捉时序数据中的长期依赖关系，适合净值趋势预测"
    "GRU" -> "门控循环单元，LSTM的轻量变体，参数更少训练更快，适合短周期预测"
    "Transformer" -> "自注意力机制模型，能并行处理序列数据，对市场结构性变化更敏感"
    "LightGBM" -> "微软开源的梯度提升框架，综合技术指标+基本面+资金流等多因子预测"
    "XGBoost" -> "极端梯度提升，正则化更强防过拟合，适合特征维度高的场景"
    "ARIMA" -> "自回归移动平均，经典统计模型，适合平稳序列但对突变不敏感"
    "纯技术面" -> "仅使用MACD/RSI/布林带/KDJ等技术指标，不含基本面和情绪因子"
    "集成模型" -> "加权融合多个模型的预测结果，通过多样性降低单一模型的偏差"
    else -> ""
}

@Composable
private fun SectionCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = FundTextSecondary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ParamSlider(
    title: String, description: String,
    value: Float, range: ClosedFloatingPointRange<Float>,
    steps: Int = 0, valueLabel: String, color: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(valueLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color)
        }
        Text(description, fontSize = 10.sp, color = FundTextSecondary, lineHeight = 13.sp)
        Slider(
            value = value, onValueChange = onValueChange,
            valueRange = range, steps = steps,
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color)
        )
    }
}
