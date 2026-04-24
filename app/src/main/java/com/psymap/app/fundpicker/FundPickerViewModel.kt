package com.psymap.app.fundpicker

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FundPickerViewModel(app: Application) : AndroidViewModel(app) {

    val repo = FundRepository(app)

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

    private val _backtestResults = MutableStateFlow<List<BacktestResult>>(emptyList())
    val backtestResults = _backtestResults.asStateFlow()

    init {
        // 启动时加载真实数据
        loadRealData()
    }

    // ==================== 数据加载 ====================

    fun loadRealData() {
        _isLoading.value = true
        _errorMsg.value = null

        // 加载基金排行（真实数据）
        repo.fetchRealFundRank(
            fundType = "all",
            pageSize = 50,
            onResult = { funds ->
                _funds.value = funds
                _topFunds.value = funds.sortedByDescending { it.aiScore }.take(10)
                _favorites.value = repo.getFavoriteFunds()
                _isLoading.value = false
                Log.d("FundVM", "加载了 ${funds.size} 只基金（真实数据）")
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
    }

    // ==================== 搜索 ====================

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _funds.value = repo.getCachedFunds()
        } else {
            _funds.value = repo.searchFunds(query)
        }
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
        _prediction.value = repo.getPrediction(fund.code, _predictionPeriod.value)
    }

    // ==================== 自选 ====================

    fun toggleFavorite(code: String) {
        if (repo.isFavorite(code)) repo.removeFavorite(code)
        else repo.addFavorite(code)
        _favorites.value = repo.getFavoriteFunds()
    }

    fun isFavorite(code: String): Boolean = repo.isFavorite(code)

    // ==================== 模拟持仓 ====================

    fun buy(fundCode: String, amount: Double): Boolean {
        val ok = repo.simulateBuy(fundCode, amount)
        if (ok) refreshPortfolio()
        return ok
    }

    fun sell(fundCode: String, amount: Double? = null, sellAll: Boolean = false): Boolean {
        val ok = repo.simulateSell(fundCode, amount, sellAll)
        if (ok) refreshPortfolio()
        return ok
    }

    fun refreshPortfolio() {
        _positions.value = repo.getPositions()
        _transactions.value = repo.getTransactions()
    }

    fun getPortfolioSummary() = repo.getPortfolioSummary()

    // ==================== AI实验室 ====================

    fun runModelCompare(fundCode: String, period: TimePeriod,
                       lstmWindow: Int = 60, wLstm: Float = 0.3f, wLgbm: Float = 0.3f,
                       wSentiment: Float = 0.15f, wTransformer: Float = 0.15f, wArima: Float = 0.1f) {
        _compareResults.value = repo.compareModels(fundCode, period, lstmWindow, wLstm, wLgbm, wSentiment, wTransformer, wArima)
        _backtestResults.value = repo.getBacktestResults(fundCode, period)
    }

    // ==================== 刷新 ====================

    fun loadRealDataByType(fundType: String) {
        _isLoading.value = true
        repo.fetchRealFundRank(
            fundType = fundType,
            pageSize = 100,
            onResult = { funds ->
                _funds.value = funds
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
    fun setRiskPreference(pref: String) = repo.setRiskPreference(pref)
    fun getDefaultPeriod() = repo.getDefaultPeriod()
    fun setDefaultPeriod(period: TimePeriod) = repo.setDefaultPeriod(period)
}
