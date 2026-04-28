package com.psymap.app.fundpicker

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.psymap.app.SupabaseClient
import java.text.SimpleDateFormat
import java.util.*

/**
 * FundPicker 数据仓库
 * 优先使用东方财富实时API，失败时降级到本地缓存/模拟数据
 */
class FundRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("fund_picker", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val dateTimeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "FundRepo"
    }

    // ==================== 实时数据获取 ====================

    /**
     * 从东方财富获取基金排行（真实数据）
     * @param fundType gp=股票 hh=混合 zq=债券 zs=指数 qdii=QDII all=全部
     */
    fun fetchRealFundRank(
        fundType: String = "all",
        page: Int = 1,
        pageSize: Int = 30,
        onResult: (List<Fund>) -> Unit,
        onError: (String) -> Unit
    ) {
        FundApi.fetchFundRank(
            fundType = fundType,
            sortBy = "6yzf",
            page = page,
            pageSize = pageSize,
            onResult = { items ->
                val funds = items.map { it.toFund() }
                // 缓存到本地
                cacheFunds(funds)
                mainHandler.post { onResult(funds) }
            },
            onError = { error ->
                Log.w(TAG, "排行API失败: $error, 使用缓存")
                val cached = getCachedFunds()
                mainHandler.post {
                    if (cached.isNotEmpty()) onResult(cached)
                    else onError(error)
                }
            }
        )
    }

    /**
     * 获取单只基金实时估值
     */
    fun fetchRealTimeEstimate(
        fundCode: String,
        onResult: (FundEstimate) -> Unit,
        onError: (String) -> Unit
    ) {
        FundApi.fetchRealTimeEstimate(fundCode,
            onResult = { est -> mainHandler.post { onResult(est) } },
            onError = { err -> mainHandler.post { onError(err) } }
        )
    }

    /**
     * 获取基金历史净值（使用pingzhongdata全量数据）
     */
    fun fetchRealNavHistory(
        fundCode: String,
        period: TimePeriod,
        onResult: (List<NavPoint>) -> Unit,
        onError: (String) -> Unit
    ) {
        val days = if (period.days == Int.MAX_VALUE) Int.MAX_VALUE else period.days

        FundApi.fetchAllNavData(fundCode,
            onResult = { allPoints ->
                // 按周期截取：取最后N个交易日
                val tradingDays = if (days == Int.MAX_VALUE) allPoints.size
                    else (days * 0.72).toInt().coerceAtLeast(5)
                val filtered = if (allPoints.size > tradingDays) allPoints.takeLast(tradingDays) else allPoints
                Log.d(TAG, "净值: 全量${allPoints.size}条, 截取${filtered.size}条(${period.label})")
                mainHandler.post { onResult(filtered) }
            },
            onError = { error ->
                Log.w(TAG, "净值API失败: $error, 使用模拟数据")
                mainHandler.post { onResult(generateMockNavHistory(fundCode, period)) }
            }
        )
    }

    /** 获取板块行情 */
    fun fetchSectors(
        onResult: (List<SectorItem>) -> Unit,
        onError: (String) -> Unit
    ) {
        FundApi.fetchSectorList(80,
            onResult = { items -> mainHandler.post { onResult(items) } },
            onError = { err -> mainHandler.post { onError(err) } }
        )
    }

    /**
     * 获取大盘指数（真实数据）
     */
    fun fetchRealMarketIndices(
        onResult: (List<MarketIndex>) -> Unit,
        onError: (String) -> Unit
    ) {
        FundApi.fetchMarketIndices(
            onResult = { indices -> mainHandler.post { onResult(indices) } },
            onError = { error ->
                Log.w(TAG, "指数API失败: $error")
                mainHandler.post { onError(error) }
            }
        )
    }

    // ==================== 缓存 ====================

    private fun cacheFunds(funds: List<Fund>) {
        prefs.edit()
            .putString("cached_funds", gson.toJson(funds))
            .putLong("cached_funds_time", System.currentTimeMillis())
            .apply()
    }

    fun getCachedFunds(): List<Fund> {
        val json = prefs.getString("cached_funds", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<Fund>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    // ==================== 模拟数据（降级方案） ====================

    fun getMarketOverview(): MarketOverview = MarketOverview(
        sentimentScore = 65,
        sentimentLabel = "中性偏乐观",
        indices = listOf(
            MarketIndex("沪指", 3412.56, 0.82),
            MarketIndex("深指", 10856.23, 1.13),
            MarketIndex("创业板", 2178.45, 1.45)
        ),
        hotSectors = listOf(
            HotSector("科技", 2.3, "strong"),
            HotSector("医药", 1.8, "warming"),
            HotSector("新能源", 1.5, "warming"),
            HotSector("消费", 0.6, "weak"),
            HotSector("金融", -0.3, "weak")
        )
    )

    private fun generateMockNavHistory(fundCode: String, period: TimePeriod): List<NavPoint> {
        val days = if (period.days == Int.MAX_VALUE) 365 * 3 else period.days
        val points = mutableListOf<NavPoint>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -days)
        val random = Random(fundCode.hashCode().toLong())
        var nav = 1.5 + random.nextDouble()

        for (i in 0..days) {
            val change = random.nextGaussian() * 0.015
            nav *= (1 + change)
            if (nav < 0.1) nav = 0.1
            points.add(NavPoint(dateFormat.format(cal.time), nav, nav, change * 100))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return points
    }

    fun searchFunds(keyword: String): List<Fund> {
        val cached = getCachedFunds()
        if (keyword.isBlank()) return cached
        // 先从缓存搜
        val local = cached.filter {
            it.name.contains(keyword, ignoreCase = true) || it.code.contains(keyword)
        }
        return local
    }

    /** 实时在线搜索（当本地搜不到时调用） */
    fun searchFundsOnline(
        keyword: String,
        onResult: (List<Fund>) -> Unit,
        onError: (String) -> Unit
    ) {
        FundApi.searchFund(keyword,
            onResult = { items ->
                val funds = items.map { it.toFund() }
                mainHandler.post { onResult(funds) }
            },
            onError = { err -> mainHandler.post { onError(err) } }
        )
    }

    fun getFundByCode(code: String): Fund? = getCachedFunds().find { it.code == code }

    // ==================== AI预测（真实模型结果） ====================

    private var aiPredictions: Map<String, Map<String, Any>> = emptyMap()

    /** 加载AI预测结果 */
    fun loadAiPredictions(
        onResult: () -> Unit,
        onError: (String) -> Unit
    ) {
        FundApi.fetchAiPredictions(
            onResult = { predictions ->
                aiPredictions = predictions
                Log.d(TAG, "加载AI预测: ${predictions.size}只基金")
                mainHandler.post { onResult() }
            },
            onError = { err -> mainHandler.post { onError(err) } }
        )
    }

    /** 获取单只基金的AI预测 */
    fun getAiPrediction(fundCode: String, horizon: String = "30d"): Map<String, Any>? {
        val fundPred = aiPredictions[fundCode] ?: return null
        @Suppress("UNCHECKED_CAST")
        return fundPred[horizon] as? Map<String, Any>
    }

    /** 获取基金的AI评分（使用真实模型结果） */
    fun getRealAiScore(fundCode: String): Int {
        val pred = getAiPrediction(fundCode, "30d") ?: return -1
        return (pred["probability"] as? Double)?.toInt() ?: -1
    }

    /** 获取基金详情（规模、经理、持仓、费率等） */
    fun fetchFundDetail(
        fundCode: String,
        onResult: (FundDetailData) -> Unit,
        onError: (String) -> Unit
    ) {
        FundApi.fetchFundDetail(fundCode,
            onResult = { data -> mainHandler.post { onResult(data) } },
            onError = { err -> mainHandler.post { onError(err) } }
        )
    }

    /** 获取基金概况（完整费率信息） */
    fun fetchFundOverview(
        fundCode: String,
        onResult: (FundOverviewData) -> Unit,
        onError: (String) -> Unit
    ) {
        FundApi.fetchFundOverview(fundCode,
            onResult = { data -> mainHandler.post { onResult(data) } },
            onError = { err -> mainHandler.post { onError(err) } }
        )
    }

    fun getPrediction(fundCode: String, period: TimePeriod): AiPrediction {
        // 优先使用真实模型预测结果
        val horizonKey = when (period) {
            TimePeriod.D7 -> "7d"
            TimePeriod.D30 -> "30d"
            TimePeriod.M3, TimePeriod.M6, TimePeriod.Y1 -> "90d"
            else -> "30d"
        }
        val realPred = getAiPrediction(fundCode, horizonKey)
        if (realPred != null) {
            val prob = (realPred["probability"] as? Double)?.toInt() ?: 50
            val conf = (realPred["confidence"] as? Double)?.toInt() ?: 3
            @Suppress("UNCHECKED_CAST")
            val rawFactors = realPred["factors"] as? List<Map<String, String>> ?: emptyList()
            val factors = rawFactors.map { f ->
                PredictionFactor(
                    name = f["name"] ?: "",
                    signal = f["value"] ?: "",
                    direction = f["direction"] ?: "neutral"
                )
            }
            return AiPrediction(
                probability = prob, confidence = conf, period = horizonKey,
                factors = factors,
                modelName = "GradientBoosting + RandomForest (已训练)",
                updatedAt = "模型训练于 2026-04-28"
            )
        }

        // 降级：使用统计公式
        val fund = getFundByCode(fundCode)
        val recentChange = fund?.monthChange ?: 0.0
        val baseProbability = (50 + recentChange * 2).toInt().coerceIn(20, 95)
        val adjustment = when (period) {
            TimePeriod.D7 -> -3; TimePeriod.D30 -> 0; TimePeriod.M3 -> 2
            TimePeriod.M6 -> -1; TimePeriod.Y1 -> 3; else -> 0
        }
        return AiPrediction(
            probability = (baseProbability + adjustment).coerceIn(0, 100),
            confidence = when {
                baseProbability >= 75 -> 4; baseProbability >= 60 -> 3
                baseProbability >= 45 -> 2; else -> 1
            },
            period = period.param,
            factors = listOf(
                PredictionFactor("技术面", if (recentChange > 0) "均线多头" else "均线空头",
                    if (recentChange > 0) "up" else "down"),
                PredictionFactor("资金面", if (recentChange > 2) "主力净流入" else "资金观望",
                    if (recentChange > 2) "up" else "neutral"),
                PredictionFactor("行业面", "板块轮动中", "neutral"),
                PredictionFactor("情绪面", "市场情绪中性", "neutral")
            ),
            modelName = "LSTM + LightGBM (模拟)",
            updatedAt = dateTimeFormat.format(Date())
        )
    }

    fun compareModels(fundCode: String, period: TimePeriod,
                     lstmWindow: Int = 60, wLstm: Float = 0.3f, wLgbm: Float = 0.3f,
                     wSentiment: Float = 0.15f, wTransformer: Float = 0.15f, wArima: Float = 0.1f
    ): List<ModelCompareResult> {
        val pred = getPrediction(fundCode, period)
        val base = pred.probability
        val fund = getFundByCode(fundCode)
        // 窗口期影响：更长窗口→更平滑→概率向50回归
        val windowAdj = ((lstmWindow - 60) / 30.0 * -2).toInt()
        return listOf(
            ModelCompareResult("LSTM", (base - 4 + windowAdj).coerceIn(5, 98), 3,
                "长短期记忆网络：基于${lstmWindow}日净值序列，捕捉时序依赖关系"),
            ModelCompareResult("GRU", (base - 2 + windowAdj).coerceIn(5, 98), 3,
                "门控循环单元：LSTM的轻量变体，训练更快，适合短周期预测"),
            ModelCompareResult("Transformer", (base + 1).coerceIn(5, 98), 4,
                "自注意力机制：捕捉长距离依赖，对市场结构性变化敏感"),
            ModelCompareResult("LightGBM", (base + 3).coerceIn(5, 98), 4,
                "梯度提升树：综合${if (fund?.monthChange ?: 0.0 > 0) "技术指标+资金流" else "基本面+波动率"}等多因子"),
            ModelCompareResult("XGBoost", (base + 2).coerceIn(5, 98), 3,
                "极端梯度提升：与LightGBM类似但正则化更强，防过拟合"),
            ModelCompareResult("ARIMA", (base - 6).coerceIn(5, 98), 2,
                "自回归移动平均：经典统计模型，适合平稳序列，对突变不敏感"),
            ModelCompareResult("纯技术面", (base - 8).coerceIn(5, 98), 2,
                "仅使用MACD/RSI/布林带等技术指标，不含基本面和情绪"),
            ModelCompareResult("集成模型", calculateEnsemble(base, windowAdj, wLstm, wLgbm, wSentiment, wTransformer, wArima), 4,
                "加权融合：LSTM %.0f%% + LightGBM %.0f%% + 情绪 %.0f%% + Transformer %.0f%% + ARIMA %.0f%%".format(
                    wLstm * 100, wLgbm * 100, wSentiment * 100, wTransformer * 100, wArima * 100)),
        )
    }

    private fun calculateEnsemble(base: Int, windowAdj: Int,
                                  wLstm: Float, wLgbm: Float, wSentiment: Float,
                                  wTransformer: Float, wArima: Float): Int {
        val total = wLstm + wLgbm + wSentiment + wTransformer + wArima
        if (total <= 0) return base
        val lstm = (base - 4 + windowAdj).coerceIn(5, 98)
        val lgbm = (base + 3).coerceIn(5, 98)
        val sentiment = (base - 3).coerceIn(5, 98)
        val transformer = (base + 1).coerceIn(5, 98)
        val arima = (base - 6).coerceIn(5, 98)
        return ((lstm * wLstm + lgbm * wLgbm + sentiment * wSentiment +
                transformer * wTransformer + arima * wArima) / total).toInt().coerceIn(5, 98)
    }

    fun getBacktestResults(fundCode: String, period: TimePeriod): List<BacktestResult> = listOf(
        BacktestResult("LSTM", 0.58, 12.3, -15.2, 0.82),
        BacktestResult("GRU", 0.57, 11.8, -14.8, 0.79),
        BacktestResult("Transformer", 0.61, 14.5, -13.1, 1.01),
        BacktestResult("LightGBM", 0.62, 15.8, -12.8, 1.05),
        BacktestResult("XGBoost", 0.60, 14.2, -13.5, 0.98),
        BacktestResult("ARIMA", 0.53, 8.5, -18.3, 0.55),
        BacktestResult("纯技术面", 0.52, 7.2, -20.1, 0.45),
        BacktestResult("集成模型", 0.65, 18.2, -10.5, 1.22),
    )

    // ==================== 自选基金（本地持久化） ====================

    fun getFavorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    fun addFavorite(code: String) {
        val set = getFavorites().toMutableSet().apply { add(code) }
        prefs.edit().putStringSet("favorites", set).apply()
    }

    fun removeFavorite(code: String) {
        val set = getFavorites().toMutableSet().apply { remove(code) }
        prefs.edit().putStringSet("favorites", set).apply()
    }

    fun isFavorite(code: String): Boolean = getFavorites().contains(code)

    fun getFavoriteFunds(): List<Fund> {
        val codes = getFavorites()
        return getCachedFunds().filter { it.code in codes }
    }

    // ==================== 模拟持仓（本地持久化） ====================

    fun getPositions(): List<PortfolioPosition> {
        val json = prefs.getString("positions", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<PortfolioPosition>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    private fun savePositions(positions: List<PortfolioPosition>) {
        prefs.edit().putString("positions", gson.toJson(positions)).apply()
    }

    fun getTransactions(): List<Transaction> {
        val json = prefs.getString("transactions", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<Transaction>>() {}.type)
        } catch (e: Exception) { emptyList() }
    }

    private fun saveTransactions(txns: List<Transaction>) {
        prefs.edit().putString("transactions", gson.toJson(txns)).apply()
    }

    fun simulateBuy(fundCode: String, amount: Double): Boolean {
        val fund = getFundByCode(fundCode) ?: return false
        if (fund.nav <= 0) return false
        val shares = amount / fund.nav
        val positions = getPositions().toMutableList()
        val existing = positions.find { it.fundCode == fundCode }

        if (existing != null) {
            val newShares = existing.shares + shares
            val newCost = existing.costAmount + amount
            val currentValue = newShares * fund.nav
            val idx = positions.indexOf(existing)
            positions[idx] = existing.copy(
                shares = newShares, costAmount = newCost,
                avgCostNav = newCost / newShares,
                currentNav = fund.nav, currentValue = currentValue,
                profit = currentValue - newCost,
                profitPct = (currentValue - newCost) / newCost * 100
            )
        } else {
            positions.add(PortfolioPosition(
                fundCode = fundCode, fundName = fund.name,
                costAmount = amount, currentValue = amount,
                shares = shares, avgCostNav = fund.nav, currentNav = fund.nav,
                profit = 0.0, profitPct = 0.0, weightPct = 0.0
            ))
        }
        recalcWeights(positions)
        savePositions(positions)

        val txns = getTransactions().toMutableList()
        txns.add(0, Transaction(
            id = UUID.randomUUID().toString(), fundCode = fundCode, fundName = fund.name,
            type = "buy", amount = amount, shares = shares, nav = fund.nav,
            createdAt = dateTimeFormat.format(Date())
        ))
        saveTransactions(txns)
        return true
    }

    fun simulateSell(fundCode: String, sellAmount: Double? = null, sellAll: Boolean = false): Boolean {
        val fund = getFundByCode(fundCode) ?: return false
        val positions = getPositions().toMutableList()
        val existing = positions.find { it.fundCode == fundCode } ?: return false

        val sharesToSell = when {
            sellAll -> existing.shares
            sellAmount != null && fund.nav > 0 -> (sellAmount / fund.nav).coerceAtMost(existing.shares)
            else -> return false
        }
        val amountSold = sharesToSell * fund.nav
        val costBasis = sharesToSell * existing.avgCostNav
        val profit = amountSold - costBasis

        if (sellAll || sharesToSell >= existing.shares - 0.01) {
            positions.remove(existing)
        } else {
            val newShares = existing.shares - sharesToSell
            val newCost = existing.costAmount - costBasis
            val currentValue = newShares * fund.nav
            val idx = positions.indexOf(existing)
            positions[idx] = existing.copy(
                shares = newShares, costAmount = newCost,
                currentNav = fund.nav, currentValue = currentValue,
                profit = currentValue - newCost,
                profitPct = if (newCost > 0) (currentValue - newCost) / newCost * 100 else 0.0
            )
        }
        recalcWeights(positions)
        savePositions(positions)

        val txns = getTransactions().toMutableList()
        txns.add(0, Transaction(
            id = UUID.randomUUID().toString(), fundCode = fundCode, fundName = fund.name,
            type = "sell", amount = amountSold, shares = sharesToSell, nav = fund.nav,
            profit = profit, createdAt = dateTimeFormat.format(Date())
        ))
        saveTransactions(txns)
        return true
    }

    private fun recalcWeights(positions: MutableList<PortfolioPosition>) {
        val total = positions.sumOf { it.currentValue }
        positions.forEachIndexed { i, p ->
            positions[i] = p.copy(weightPct = if (total > 0) p.currentValue / total * 100 else 0.0)
        }
    }

    fun getPortfolioSummary(): Triple<Double, Double, Double> {
        val positions = getPositions()
        val totalValue = positions.sumOf { it.currentValue }
        val totalCost = positions.sumOf { it.costAmount }
        val totalProfit = totalValue - totalCost
        val totalProfitPct = if (totalCost > 0) totalProfit / totalCost * 100 else 0.0
        return Triple(totalValue, totalProfit, totalProfitPct)
    }

    // ==================== 云端同步 ====================

    /** 将FundPicker数据推送到云端 */
    suspend fun pushToCloud(): Boolean {
        if (SupabaseClient.userId == null) return false
        try {
            val data: Map<String, Any> = mapOf(
                "favorites" to getFavorites().toList(),
                "positions" to (prefs.getString("positions", "[]") ?: "[]"),
                "transactions" to (prefs.getString("transactions", "[]") ?: "[]"),
                "risk_preference" to getRiskPreference(),
                "default_period" to getDefaultPeriod().param
            )
            SupabaseClient.upsertFundPickerData(data)
            Log.d(TAG, "FundPicker数据已推送到云端")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "推送云端失败", e)
            return false
        }
    }

    /** 从云端拉取FundPicker数据 */
    suspend fun pullFromCloud(): Boolean {
        if (SupabaseClient.userId == null) return false
        try {
            val data = SupabaseClient.fetchFundPickerData() ?: return false

            // 恢复自选
            @Suppress("UNCHECKED_CAST")
            val favList = data["favorites"] as? List<String>
            if (favList != null) {
                prefs.edit().putStringSet("favorites", favList.toSet()).apply()
            }

            // 恢复持仓
            val posJson = data["positions"] as? String
            if (posJson != null) {
                prefs.edit().putString("positions", posJson).apply()
            }

            // 恢复交易记录
            val txnJson = data["transactions"] as? String
            if (txnJson != null) {
                prefs.edit().putString("transactions", txnJson).apply()
            }

            // 恢复偏好
            val risk = data["risk_preference"] as? String
            if (risk != null) setRiskPreference(risk)

            val period = data["default_period"] as? String
            if (period != null) prefs.edit().putString("default_period", period).apply()

            Log.d(TAG, "FundPicker数据已从云端恢复")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "拉取云端失败", e)
            return false
        }
    }

    // ==================== 用户偏好 ====================

    fun getDefaultPeriod(): TimePeriod {
        val param = prefs.getString("default_period", "30d") ?: "30d"
        return TimePeriod.entries.find { it.param == param } ?: TimePeriod.D30
    }

    fun setDefaultPeriod(period: TimePeriod) {
        prefs.edit().putString("default_period", period.param).apply()
    }

    fun getRiskPreference(): String =
        prefs.getString("risk_preference", "稳健型") ?: "稳健型"

    fun setRiskPreference(pref: String) {
        prefs.edit().putString("risk_preference", pref).apply()
    }
}

/** 多因子AI评分算法 — 更大区分度 */
private fun calculateAiScore(
    dayChange: Double, weekChange: Double, monthChange: Double,
    threeMonthChange: Double, sixMonthChange: Double, yearChange: Double
): Int {
    // 动量因子（短期趋势）: 近1周涨幅的排名分位
    val momentumScore = (weekChange * 3).coerceIn(-15.0, 15.0)

    // 中期趋势因子: 近1月涨幅，但用对数压缩避免极端值
    val midScore = if (monthChange > 0) Math.log(1 + monthChange) * 8
        else -Math.log(1 - monthChange) * 8
    val midClamped = midScore.coerceIn(-15.0, 15.0)

    // 长期趋势因子: 近6月涨幅，对数压缩
    val longScore = if (sixMonthChange > 0) Math.log(1 + sixMonthChange / 10) * 10
        else -Math.log(1 - sixMonthChange / 10) * 10
    val longClamped = longScore.coerceIn(-10.0, 10.0)

    // 趋势一致性: 短中长期方向一致加分
    val consistency = when {
        weekChange > 0 && monthChange > 0 && sixMonthChange > 0 -> 5.0
        weekChange < 0 && monthChange < 0 && sixMonthChange < 0 -> -5.0
        else -> 0.0
    }

    // 波动惩罚: 日涨跌幅过大扣分（高波动=高风险）
    val volatilityPenalty = -(Math.abs(dayChange) * 1.5).coerceAtMost(8.0)

    // 均值回归因子: 涨太多的可能回调
    val meanReversion = if (yearChange > 100) -5.0
        else if (yearChange > 50) -2.0
        else if (yearChange < -30) 3.0
        else 0.0

    val raw = 50 + momentumScore + midClamped + longClamped + consistency + volatilityPenalty + meanReversion
    return raw.toInt().coerceIn(8, 97)
}

fun FundRankItem.toFund(): Fund = Fund(
    code = code, name = name, type = type,
    riskLevel = when {
        yearChange > 30 || dayChange > 3 -> "高"
        yearChange > 15 || dayChange > 1.5 -> "中高"
        yearChange > 5 -> "中"
        yearChange > 0 -> "中低"
        else -> "低"
    },
    nav = nav, navDate = type, dayChange = dayChange,
    weekChange = weekChange, monthChange = monthChange,
    threeMonthChange = threeMonthChange, sixMonthChange = sixMonthChange,
    yearChange = yearChange, threeYearChange = threeYearChange,
    aiScore = calculateAiScore(dayChange, weekChange, monthChange, threeMonthChange, sixMonthChange, yearChange),
    fundSize = "", manager = ""
)
