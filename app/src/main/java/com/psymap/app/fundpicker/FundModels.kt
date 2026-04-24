package com.psymap.app.fundpicker

/** 时间维度枚举 */
enum class TimePeriod(val label: String, val param: String, val days: Int) {
    D7("7天", "7d", 7),
    D30("30天", "30d", 30),
    M3("3月", "3m", 90),
    M6("6月", "6m", 180),
    Y1("1年", "1y", 365),
    Y3("3年", "3y", 1095),
    ALL("全部", "all", Int.MAX_VALUE)
}

/** 基金类型 */
enum class FundType(val label: String) {
    ALL("全部"), STOCK("股票型"), MIXED("混合型"),
    BOND("债券型"), INDEX("指数型"), QDII("QDII")
}

/** 风险等级 */
enum class RiskLevel(val label: String) {
    ALL("全部"), LOW("低"), MEDIUM_LOW("中低"),
    MEDIUM("中"), MEDIUM_HIGH("中高"), HIGH("高")
}

/** 基金基本信息 */
data class Fund(
    val code: String = "",
    val name: String = "",
    val type: String = "",
    val riskLevel: String = "",
    val nav: Double = 0.0,          // 最新净值
    val navDate: String = "",
    val dayChange: Double = 0.0,    // 日涨跌幅 %
    val weekChange: Double = 0.0,
    val monthChange: Double = 0.0,
    val threeMonthChange: Double = 0.0,
    val sixMonthChange: Double = 0.0,
    val yearChange: Double = 0.0,
    val aiScore: Int = 0,           // AI预测上涨概率 0-100
    val fundSize: String = "",      // 基金规模
    val manager: String = "",       // 基金经理
)

/** 净值数据点 */
data class NavPoint(
    val date: String = "",
    val nav: Double = 0.0,
    val accNav: Double = 0.0,
    val changePct: Double = 0.0
)

/** AI预测结果 */
data class AiPrediction(
    val probability: Int = 0,       // 上涨概率 0-100
    val confidence: Int = 0,        // 置信度 1-5
    val period: String = "30d",
    val factors: List<PredictionFactor> = emptyList(),
    val modelName: String = "",
    val updatedAt: String = ""
)

data class PredictionFactor(
    val name: String = "",          // 如 "技术面"
    val signal: String = "",        // 如 "MACD金叉"
    val direction: String = ""      // "up" / "down" / "neutral"
)

/** AI模型对比结果 */
data class ModelCompareResult(
    val modelName: String = "",
    val probability: Int = 0,
    val confidence: Int = 0,
    val description: String = "",
    val predictedCurve: List<NavPoint> = emptyList()
)

/** 回测结果 */
data class BacktestResult(
    val modelName: String = "",
    val hitRate: Double = 0.0,
    val annualReturn: Double = 0.0,
    val maxDrawdown: Double = 0.0,
    val sharpeRatio: Double = 0.0
)

/** 持仓头寸 */
data class PortfolioPosition(
    val fundCode: String = "",
    val fundName: String = "",
    val costAmount: Double = 0.0,   // 投入金额
    val currentValue: Double = 0.0, // 当前市值
    val shares: Double = 0.0,       // 持有份额
    val avgCostNav: Double = 0.0,   // 平均成本净值
    val currentNav: Double = 0.0,   // 当前净值
    val profit: Double = 0.0,       // 盈亏金额
    val profitPct: Double = 0.0,    // 盈亏比例 %
    val weightPct: Double = 0.0     // 仓位占比 %
)

/** 模拟交易记录 */
data class Transaction(
    val id: String = "",
    val fundCode: String = "",
    val fundName: String = "",
    val type: String = "",          // "buy" / "sell"
    val amount: Double = 0.0,
    val shares: Double = 0.0,
    val nav: Double = 0.0,
    val profit: Double = 0.0,       // 卖出时的盈亏
    val createdAt: String = ""
)

/** 市场概览 */
data class MarketOverview(
    val sentimentScore: Int = 50,   // 0-100
    val sentimentLabel: String = "中性",
    val indices: List<MarketIndex> = emptyList(),
    val hotSectors: List<HotSector> = emptyList()
)

data class MarketIndex(
    val name: String = "",
    val value: Double = 0.0,
    val changePct: Double = 0.0
)

data class HotSector(
    val name: String = "",
    val changePct: Double = 0.0,
    val status: String = ""         // "strong" / "warming" / "weak"
)
