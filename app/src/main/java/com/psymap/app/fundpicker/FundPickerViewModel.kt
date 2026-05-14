package com.psymap.app.fundpicker

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FundPickerViewModel(app: Application) : AndroidViewModel(app) {

    val repo = FundRepository(app)

    // AI预测数据（StateFlow，UI 可直接观察）
    private val _aiPredictions = MutableStateFlow<Map<String, Map<String, Any>>>(emptyMap())
    val aiPredictions = _aiPredictions.asStateFlow()

    // 回测胜率：按档位聚合
    // [{"bucket":"70-80","bucket_min":70,"bucket_max":80,"total_count":1243,"win_count":722,"win_rate":0.58,"avg_actual_return":3.2,...}]
    private val _backtestBuckets = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val backtestBuckets = _backtestBuckets.asStateFlow()

    /** 获取基金AI分数（从StateFlow读取，保证一致性） */
    fun getAiScore(code: String): Int {
        val pred = _aiPredictions.value[code] ?: return 0
        return if (pred.containsKey("probability")) {
            (pred["probability"] as? Double)?.toInt() ?: 0
        } else {
            @Suppress("UNCHECKED_CAST")
            val sub = pred["30d"] as? Map<String, Any>
            (sub?.get("probability") as? Double)?.toInt() ?: 0
        }
    }

    /** 获取基金置信度 */
    fun getAiConfidence(code: String): Int {
        val pred = _aiPredictions.value[code] ?: return 0
        return (pred["confidence"] as? Double)?.toInt() ?: 0
    }

    /** 获取基金风险指标（夏普/回撤/正收益率） */
    fun getRiskMetrics(code: String): Triple<Double, Double, Double> {
        val pred = _aiPredictions.value[code] ?: return Triple(0.0, 100.0, 0.0)
        val sharpe = (pred["sharpe"] as? Double) ?: 0.0
        val maxDd = (pred["max_drawdown"] as? Double) ?: 100.0
        val posPct = (pred["positive_pct"] as? Double) ?: 0.0
        return Triple(sharpe, maxDd, posPct)
    }

    // 市场概览
    private val _market = MutableStateFlow(repo.getMarketOverview())
    val market = _market.asStateFlow()

    // 是否正在加载
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // 错误信息
    private val _errorMsg = MutableStateFlow<String?>(null)
    val errorMsg = _errorMsg.asStateFlow()

    // AI精选（按AI评分排序的Top基金）
    private val _topFunds = MutableStateFlow<List<Fund>>(emptyList())
    val topFunds = _topFunds.asStateFlow()

    // 全部基金 / 搜索结果
    private val _funds = MutableStateFlow<List<Fund>>(emptyList())
    val funds = _funds.asStateFlow()

    // 搜索关键词
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // 当前选中基金
    private val _selectedFund = MutableStateFlow<Fund?>(null)
    val selectedFund = _selectedFund.asStateFlow()

    // 实时估值
    private val _estimate = MutableStateFlow<FundEstimate?>(null)
    val estimate = _estimate.asStateFlow()

    // 净值走势
    private val _navHistory = MutableStateFlow<List<NavPoint>>(emptyList())
    val navHistory = _navHistory.asStateFlow()

    // AI预测
    private val _prediction = MutableStateFlow<AiPrediction?>(null)
    val prediction = _prediction.asStateFlow()

    // 预测周期
    private val _predictionPeriod = MutableStateFlow(TimePeriod.D30)
    val predictionPeriod = _predictionPeriod.asStateFlow()

    // 净值图表周期
    private val _chartPeriod = MutableStateFlow(TimePeriod.M3)
    val chartPeriod = _chartPeriod.asStateFlow()

    // 自选
    private val _favorites = MutableStateFlow<List<Fund>>(emptyList())
    val favorites = _favorites.asStateFlow()

    // 持仓
    private val _positions = MutableStateFlow<List<PortfolioPosition>>(emptyList())
    val positions = _positions.asStateFlow()

    private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    // 基金详情数据
    private val _fundDetail = MutableStateFlow<FundDetailData?>(null)
    val fundDetail = _fundDetail.asStateFlow()

    // 基金概况（费率等）
    private val _fundOverview = MutableStateFlow<FundOverviewData?>(null)
    val fundOverview = _fundOverview.asStateFlow()

    // AI模型对比
    private val _compareResults = MutableStateFlow<List<ModelCompareResult>>(emptyList())
    val compareResults = _compareResults.asStateFlow()

    // 板块行情
    private val _sectors = MutableStateFlow<List<SectorItem>>(emptyList())
    val sectors = _sectors.asStateFlow()

    // 板块详情（含资金流向）
    private val _sectorDetails = MutableStateFlow<List<SectorDetail>>(emptyList())
    val sectorDetails = _sectorDetails.asStateFlow()

    // 板块排序方式
    private val _sectorSortType = MutableStateFlow(SectorSortType.CHANGE)
    val sectorSortType = _sectorSortType.asStateFlow()

    // 板块时间维度
    private val _sectorTimePeriod = MutableStateFlow(SectorTimePeriod.D1)
    val sectorTimePeriod = _sectorTimePeriod.asStateFlow()

    // 当前选中板块
    private val _selectedSector = MutableStateFlow<SectorDetail?>(null)
    val selectedSector = _selectedSector.asStateFlow()

    // 板块关联基金
    private val _sectorFunds = MutableStateFlow<List<SectorFund>>(emptyList())
    val sectorFunds = _sectorFunds.asStateFlow()

    // 板块基金加载中
    private val _sectorFundsLoading = MutableStateFlow(false)
    val sectorFundsLoading = _sectorFundsLoading.asStateFlow()

    private val _backtestResults = MutableStateFlow<List<BacktestResult>>(emptyList())
    val backtestResults = _backtestResults.asStateFlow()

    init {
        // 先从本地读自选和持仓（即使云端还没 pull，本地可能有上次的数据）
        _favorites.value = repo.getFavoriteFunds()
        refreshPortfolio()

        loadRealData()
        // 触发云端全量预测
        FundApi.triggerUpdate(
            onResult = { Log.d("FundVM", "触发云端更新: $it") },
            onError = { }
        )
        // 加载回测胜率（有数据就显示，没数据不阻塞 UI）
        FundApi.fetchBacktest(30,
            onResult = { buckets ->
                _backtestBuckets.value = buckets
                Log.d("FundVM", "回测胜率加载: ${buckets.size}档")
            },
            onError = { Log.w("FundVM", "回测胜率加载失败: $it") }
        )
        // 从云端获取TOP10（有延迟，先用predictions.json兜底）
        repo.loadAiPredictions(
            onResult = {
                val rawPreds = repo.getAllPredictionsRaw()
                Log.d("FundVM", "AI预测加载完成: ${rawPreds.size}只基金")
                _aiPredictions.value = rawPreds
                val allPreds = repo.getAllPredictionScores()
                val fallback = allPreds.entries
                    .sortedByDescending { it.value }
                    .take(10)
                    .map { (code, score) -> Fund(code = code, name = code, aiScore = score) }
                if (fallback.isNotEmpty() && _topFunds.value.isEmpty()) {
                    _topFunds.value = fallback
                }
                // 然后尝试云端TOP10覆盖
                loadCloudTop10()
            },
            onError = { loadCloudTop10() }
        )
        viewModelScope.launch {
            Log.d("FundVM", "pullFromCloud: userId=${com.psymap.app.SupabaseClient.userId}")
            val pulled = repo.pullFromCloud()
            Log.d("FundVM", "pullFromCloud result=$pulled")
            if (pulled) {
                // 云端恢复成功，重新读取（云端数据覆盖本地）
                _favorites.value = repo.getFavoriteFunds()
                refreshPortfolio()
                Log.d("FundVM", "云端恢复: 自选${_favorites.value.size}只, 持仓${_positions.value.size}只")
            } else {
                Log.w("FundVM", "云端恢复失败，本地自选${_favorites.value.size}只, 持仓${_positions.value.size}只")
            }
            enrichFavoriteDetails()
        }
    }

    /** 对自选列表中信息不完整的基金（名称=代码或占位），在线补全 */
    fun enrichFavoriteDetails() {
        val toEnrich = _favorites.value.filter {
            it.name.startsWith("基金") || it.nav == 0.0 || it.name == it.code
        }
        Log.d("FundVM", "需要补全自选信息: ${toEnrich.size}只")
        toEnrich.forEach { fund ->
            FundApi.searchFund(fund.code,
                onResult = { items ->
                    val match = items.firstOrNull { it.code == fund.code }
                    if (match != null && match.name.isNotBlank()) {
                        val realFund = match.toFund().copy(
                            aiScore = repo.getRealAiScore(fund.code).coerceAtLeast(0)
                        )
                        repo.ensureFundCached(realFund)
                        // 更新 StateFlow 里对应位置
                        _favorites.value = _favorites.value.map { f ->
                            if (f.code == fund.code) realFund else f
                        }
                        Log.d("FundVM", "补全自选: ${fund.code} -> ${realFund.name}")
                    } else {
                        Log.w("FundVM", "搜索${fund.code}无结果")
                    }
                },
                onError = { Log.w("FundVM", "搜索${fund.code}失败: $it") }
            )
        }
    }

    /** 从云端API获取TOP10 */
    fun loadCloudTop10() {
        FundApi.fetchTop10(
            onResult = { top10List ->
                if (top10List.isNotEmpty()) {
                    val funds = top10List.mapNotNull { item ->
                        val code = item["code"] as? String ?: return@mapNotNull null
                        val name = item["name"] as? String ?: code
                        val prob = (item["probability"] as? Double)?.toInt() ?: 0
                        val conf = (item["confidence"] as? Double)?.toInt() ?: 0
                        Fund(code = code, name = name, aiScore = prob)
                    }
                    if (funds.isNotEmpty()) {
                        _topFunds.value = funds
                        Log.d("FundVM", "云端TOP10已加载: ${funds.size}只")
                        // 为每只 TOP10 基金补充近30日净值涨幅
                        funds.forEach { fund -> enrichTopFundMonthChange(fund.code) }
                    }
                }
            },
            onError = { Log.w("FundVM", "获取TOP10失败: $it") }
        )
    }

    /** 为 TOP10 单只基金拉最近30个交易日净值，计算 monthChange */
    private fun enrichTopFundMonthChange(fundCode: String) {
        FundApi.fetchAllNavData(fundCode,
            onResult = { allPoints ->
                if (allPoints.size < 2) return@fetchAllNavData
                // 近30交易日：~22个交易日 ≈ 30自然日
                val recent = if (allPoints.size >= 22) allPoints.takeLast(22) else allPoints
                val first = recent.first().nav
                val last = recent.last().nav
                if (first > 0) {
                    val change = (last - first) / first * 100
                    _topFunds.value = _topFunds.value.map { f ->
                        if (f.code == fundCode) f.copy(monthChange = change, nav = last)
                        else f
                    }
                }
            },
            onError = { }
        )
    }

    // ==================== 数据加载 ====================

    fun loadRealData() {
        _isLoading.value = true
        _errorMsg.value = null

        // 加载基金排行（真实数据）
        repo.fetchRealFundRank(
            fundType = "all",
            pageSize = 200,
            onResult = { funds ->
                _funds.value = funds
                _isLoading.value = false
                // 排行加载完后再加载AI预测并更新
                repo.loadAiPredictions(
                    onResult = {
                        _aiPredictions.value = repo.getAllPredictionsRaw()
                        val updated = funds.map { f ->
                            val s = repo.getRealAiScore(f.code)
                            if (s >= 0) f.copy(aiScore = s) else f.copy(aiScore = 0)
                        }
                        _funds.value = updated
                        // 只更新自选基金的 AI 分数，不覆盖自选列表本身
                        _favorites.value = _favorites.value.map { f ->
                            val s = repo.getRealAiScore(f.code)
                            if (s >= 0) f.copy(aiScore = s) else f
                        }
                        // 不设置TOP10，让云端/top10的结果生效
                        // 再次调用云端TOP10确保覆盖
                        loadCloudTop10()
                        // 补全自选占位基金的真实名称
                        enrichFavoriteDetails()
                    },
                    onError = {
                        _favorites.value = _favorites.value.ifEmpty { repo.getFavoriteFunds() }
                        loadCloudTop10()
                        enrichFavoriteDetails()
                    }
                )
            },
            onError = { error ->
                _errorMsg.value = error
                _isLoading.value = false
                Log.e("FundVM", "加载基金数据失败: $error")
            }
        )

        // 加载大盘指数（真实数据）
        repo.fetchRealMarketIndices(
            onResult = { indices ->
                val current = _market.value
                _market.value = current.copy(indices = indices)
            },
            onError = { }
        )

        // 加载板块行情
        repo.fetchSectors(
            onResult = { items -> _sectors.value = items },
            onError = { }
        )

        // 加载板块详情（含资金流向）
        loadSectorDetails()
    }

    // ==================== 搜索 ====================

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _funds.value = repo.getCachedFunds().map { applyRealScore(it) }
            return
        }
        val local = repo.searchFunds(query).map { applyRealScore(it) }
        _funds.value = local
        if (query.length >= 2) {
            repo.searchFundsOnline(query,
                onResult = { online ->
                    val merged = (online.map { applyRealScore(it) } + local).distinctBy { it.code }
                    _funds.value = merged
                    // 对没有预测的基金，异步调云端API获取评分
                    merged.filter { it.aiScore == 0 }.take(5).forEach { fund ->
                        repo.fetchCloudPrediction(fund.code, 30,
                            onResult = { pred ->
                                // 1. 更新 _funds 列表显示
                                _funds.value = _funds.value.map { f ->
                                    if (f.code == fund.code) f.copy(aiScore = pred.probability) else f
                                }
                                // 2. 同步写入 _aiPredictions，保证详情页能读到（数据源一致性）
                                val factorsMap = pred.factors.map { ft ->
                                    mapOf("name" to ft.name, "value" to ft.signal, "direction" to ft.direction)
                                }
                                val cachedPred: Map<String, Any> = mapOf(
                                    "probability" to pred.probability.toDouble(),
                                    "confidence" to pred.confidence.toDouble(),
                                    "factors" to factorsMap,
                                    "name" to fund.name
                                )
                                _aiPredictions.value = _aiPredictions.value.toMutableMap().apply {
                                    put(fund.code, cachedPred)
                                }
                                Log.d("FundVM", "云端实时预测已写入 _aiPredictions: ${fund.code}=${pred.probability}%")
                            },
                            onError = { }
                        )
                    }
                },
                onError = { }
            )
        }
    }

    private fun applyRealScore(fund: Fund): Fund {
        val realScore = repo.getRealAiScore(fund.code)
        return if (realScore >= 0) fund.copy(aiScore = realScore) else fund.copy(aiScore = 0)
    }

    // ==================== 基金详情 ====================

    fun selectFund(fund: Fund) {
        _selectedFund.value = fund
        _estimate.value = null
        _fundDetail.value = null
        _fundOverview.value = null
        loadNavHistory()
        loadPrediction()
        loadRealTimeEstimate(fund.code)
        loadFundDetail(fund.code)
        loadFundOverview(fund.code)
    }

    fun selectFundByCode(code: String) {
        repo.getFundByCode(code)?.let { selectFund(it) }
    }

    private fun loadRealTimeEstimate(fundCode: String) {
        repo.fetchRealTimeEstimate(fundCode,
            onResult = { est -> _estimate.value = est },
            onError = { /* 忽略，使用静态净值 */ }
        )
    }

    private fun loadFundDetail(fundCode: String) {
        repo.fetchFundDetail(fundCode,
            onResult = { detail -> _fundDetail.value = detail },
            onError = { /* 忽略 */ }
        )
    }

    private fun loadFundOverview(fundCode: String) {
        repo.fetchFundOverview(fundCode,
            onResult = { overview -> _fundOverview.value = overview },
            onError = { /* 忽略 */ }
        )
    }

    fun setChartPeriod(period: TimePeriod) {
        _chartPeriod.value = period
        loadNavHistory()
    }

    fun setPredictionPeriod(period: TimePeriod) {
        _predictionPeriod.value = period
        loadPrediction()
    }

    private fun loadNavHistory() {
        val fund = _selectedFund.value ?: return
        _navHistory.value = emptyList() // 清空触发loading
        repo.fetchRealNavHistory(fund.code, _chartPeriod.value,
            onResult = { points -> _navHistory.value = points },
            onError = { /* 已在repo内降级到模拟数据 */ }
        )
    }

    private fun loadPrediction() {
        val fund = _selectedFund.value ?: return
        // 从 ViewModel 的 aiPredictions StateFlow 读取（和列表页同源）
        val cachedPred = _aiPredictions.value[fund.code]
        if (cachedPred != null) {
            val prob = if (cachedPred.containsKey("probability"))
                (cachedPred["probability"] as? Double)?.toInt() ?: 0 else 0
            val conf = (cachedPred["confidence"] as? Double)?.toInt() ?: 3
            @Suppress("UNCHECKED_CAST")
            val rawFactors = cachedPred["factors"] as? List<Map<String, String>> ?: emptyList()
            val factors = rawFactors.map { f ->
                PredictionFactor(f["name"] ?: "", f["value"] ?: "", f["direction"] ?: "neutral")
            }
            _prediction.value = AiPrediction(
                probability = prob, confidence = conf, period = "30d",
                factors = factors, modelName = "AI模型预测", updatedAt = ""
            )
            return
        }
        // Supabase 没有数据 — 先显示占位，然后异步调云端实时 API
        _prediction.value = null
        repo.fetchCloudPrediction(fund.code, 30,
            onResult = { pred ->
                // 仅当用户还在该基金详情页时才更新
                if (_selectedFund.value?.code == fund.code) {
                    _prediction.value = pred
                }
                // 同步写入 _aiPredictions 供列表页使用
                val factorsMap = pred.factors.map { ft ->
                    mapOf("name" to ft.name, "value" to ft.signal, "direction" to ft.direction)
                }
                val newPred: Map<String, Any> = mapOf(
                    "probability" to pred.probability.toDouble(),
                    "confidence" to pred.confidence.toDouble(),
                    "factors" to factorsMap,
                    "name" to fund.name
                )
                _aiPredictions.value = _aiPredictions.value.toMutableMap().apply {
                    put(fund.code, newPred)
                }
                // 同时更新 _funds 中的 aiScore（搜索结果也会一致）
                _funds.value = _funds.value.map { f ->
                    if (f.code == fund.code) f.copy(aiScore = pred.probability) else f
                }
                Log.d("FundVM", "详情页实时预测: ${fund.code}=${pred.probability}%")
            },
            onError = { err ->
                Log.w("FundVM", "详情页实时预测失败: ${fund.code}, $err")
            }
        )
    }

    // ==================== 自选 ====================

    fun toggleFavorite(code: String) {
        if (repo.isFavorite(code)) {
            repo.removeFavorite(code)
        } else {
            repo.addFavorite(code)
            // 确保当前基金在缓存中（否则自选页找不到）
            val currentFund = _selectedFund.value
            if (currentFund != null && currentFund.code == code) {
                repo.ensureFundCached(currentFund)
            }
        }
        _favorites.value = repo.getFavoriteFunds()
        pushToCloud()
    }

    fun isFavorite(code: String): Boolean = repo.isFavorite(code)

    // ==================== 模拟持仓 ====================

    fun buy(fundCode: String, amount: Double): Boolean {
        // 读取当前 AI 预测率，作为买入时的快照保存
        val currentAi = _aiPredictions.value[fundCode]?.let { pred ->
            if (pred.containsKey("probability")) (pred["probability"] as? Double)?.toInt() ?: 0 else 0
        } ?: 0
        // 获取实时估值净值作为 fallback（当缓存 nav=0 时使用）
        val estimateNav = _estimate.value?.estimateNav ?: 0.0
        val navHistory = _navHistory.value
        val fallbackNav = if (estimateNav > 0) estimateNav
            else if (navHistory.isNotEmpty()) navHistory.last().nav
            else 0.0
        val ok = repo.simulateBuy(fundCode, amount, currentAi, fallbackNav)
        if (ok) { refreshPortfolio(); pushToCloud() }
        return ok
    }

    fun sell(fundCode: String, amount: Double? = null, sellAll: Boolean = false): Boolean {
        val ok = repo.simulateSell(fundCode, amount, sellAll)
        if (ok) { refreshPortfolio(); pushToCloud() }
        return ok
    }

    fun refreshPortfolio() {
        val positions = repo.getPositions()
        // 迁移：老持仓没有 buyAiScore，用当前 aiPredictions 里的值作为兜底
        val migrated = positions.map { p ->
            if (p.buyAiScore == 0) {
                val currentAi = _aiPredictions.value[p.fundCode]?.let { pred ->
                    if (pred.containsKey("probability"))
                        (pred["probability"] as? Double)?.toInt() ?: 0 else 0
                } ?: 0
                if (currentAi > 0) p.copy(buyAiScore = currentAi) else p
            } else p
        }
        _positions.value = migrated
        if (migrated != positions) {
            // 回写 buyAiScore 到本地
            repo.savePositionsPublic(migrated)
        }
        _transactions.value = repo.getTransactions()
        // 用实时估值更新每只持仓基金的当前净值和收益
        migrated.forEach { pos ->
            FundApi.fetchRealTimeEstimate(pos.fundCode,
                onResult = { est ->
                    if (est.estimateNav > 0 && est.estimateNav != pos.currentNav) {
                        val newValue = pos.shares * est.estimateNav
                        val newProfit = newValue - pos.costAmount
                        val newProfitPct = if (pos.costAmount > 0) newProfit / pos.costAmount * 100 else 0.0
                        _positions.value = _positions.value.map { p ->
                            if (p.fundCode == pos.fundCode) p.copy(
                                currentNav = est.estimateNav,
                                currentValue = newValue,
                                profit = newProfit,
                                profitPct = newProfitPct
                            ) else p
                        }
                    }
                },
                onError = { }
            )
        }
    }

    fun getPortfolioSummary() = repo.getPortfolioSummary()

    // ==================== AI实验室 ====================

    fun runModelCompare(fundCode: String, period: TimePeriod,
                       lstmWindow: Int = 60, wLstm: Float = 0.3f, wLgbm: Float = 0.3f,
                       wSentiment: Float = 0.15f, wTransformer: Float = 0.15f, wArima: Float = 0.1f) {
        _compareResults.value = repo.compareModels(fundCode, period, lstmWindow, wLstm, wLgbm, wSentiment, wTransformer, wArima)
        _backtestResults.value = repo.getBacktestResults(fundCode, period)
    }

    // ==================== 行业板块 ====================

    fun loadSectorDetails() {
        val sortField = when (_sectorSortType.value) {
            SectorSortType.CHANGE -> "f3"
            SectorSortType.CAPITAL -> "f62"
        }
        repo.fetchSectorDetails(sortField, _sectorTimePeriod.value.param,
            onResult = { items -> _sectorDetails.value = items },
            onError = { }
        )
    }

    fun setSectorSortType(type: SectorSortType) {
        _sectorSortType.value = type
        loadSectorDetails()
    }

    fun setSectorTimePeriod(period: SectorTimePeriod) {
        _sectorTimePeriod.value = period
        loadSectorDetails()
    }

    fun selectSector(sector: SectorDetail) {
        _selectedSector.value = sector
        _sectorFunds.value = emptyList()
        _sectorFundsLoading.value = true

        val keywords = FundApi.SECTOR_LIST.find { it.first == sector.name }?.second
        Log.d("FundVM", "selectSector: ${sector.name}, keywords=$keywords")
        if (keywords.isNullOrEmpty()) {
            Log.w("FundVM", "板块关键词为空!")
            _sectorFundsLoading.value = false
            return
        }

        // 用东方财富排行接口获取1000只股票型基金，按关键词筛选
        repo.fetchRealFundRank(
            fundType = "gp",
            pageSize = 1000,
            onResult = { allFunds ->
                Log.d("FundVM", "排行返回: ${allFunds.size}只, 前3: ${allFunds.take(3).map { it.name }}")
                val filtered = allFunds.filter { fund ->
                    keywords.any { kw -> fund.name.contains(kw) }
                }
                Log.d("FundVM", "关键词筛选后: ${filtered.size}只")
                val sectorFundList = filtered.map { fund ->
                    val aiScore = repo.getRealAiScore(fund.code)
                    SectorFund(
                        fundCode = fund.code, fundName = fund.name,
                        nav = fund.nav, yearChange = fund.yearChange,
                        dayChange = fund.dayChange, fundScale = fund.fundSize,
                        aiScore = if (aiScore >= 0) aiScore else 0
                    )
                }.sortedByDescending { it.yearChange }
                _sectorFunds.value = sectorFundList
                _sectorFundsLoading.value = false
            },
            onError = { error ->
                Log.e("FundVM", "排行获取失败: $error")
                _sectorFundsLoading.value = false
            }
        )
    }

    fun clearSelectedSector() {
        _selectedSector.value = null
        _sectorFunds.value = emptyList()
    }

    // ==================== 刷新 ====================

    fun loadRealDataByType(fundType: String) {
        _isLoading.value = true
        repo.fetchRealFundRank(
            fundType = fundType,
            pageSize = 100,
            onResult = { funds ->
                _funds.value = funds.map { applyRealScore(it) }
                _isLoading.value = false
            },
            onError = { _isLoading.value = false }
        )
    }

    fun refresh() {
        loadRealData()
        refreshPortfolio()
    }

    // ==================== 用户偏好 ====================

    fun getRiskPreference() = repo.getRiskPreference()
    fun setRiskPreference(pref: String) { repo.setRiskPreference(pref); pushToCloud() }
    fun getDefaultPeriod() = repo.getDefaultPeriod()
    fun setDefaultPeriod(period: TimePeriod) { repo.setDefaultPeriod(period); pushToCloud() }

    private fun pushToCloud() {
        viewModelScope.launch {
            repo.pushToCloud()
        }
    }
}
