package com.psymap.app.fundpicker

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 东方财富/天天基金 公开API封装
 */
object FundApi {

    private const val TAG = "FundApi"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // ==================== 基金实时估值 ====================
    fun fetchRealTimeEstimate(
        fundCode: String,
        onResult: (FundEstimate) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://fundgz.1234567.com.cn/js/$fundCode.js?v=${System.currentTimeMillis()}"
        request(url, { body ->
            try {
                val json = body.substringAfter("jsonpgz(").substringBeforeLast(")")
                val map = gson.fromJson<Map<String, String>>(json,
                    object : TypeToken<Map<String, String>>() {}.type)
                onResult(FundEstimate(
                    fundCode = map["fundcode"] ?: fundCode,
                    name = map["name"] ?: "",
                    navDate = map["jzrq"] ?: "",
                    nav = map["dwjz"]?.toDoubleOrNull() ?: 0.0,
                    estimateNav = map["gsz"]?.toDoubleOrNull() ?: 0.0,
                    estimateChangePct = map["gszzl"]?.toDoubleOrNull() ?: 0.0,
                    estimateTime = map["gztime"] ?: ""
                ))
            } catch (e: Exception) { onError("解析失败: ${e.message}") }
        }, onError)
    }

    // ==================== 基金排行榜 ====================
    fun fetchFundRank(
        fundType: String = "all",
        sortBy: String = "6yzf",
        page: Int = 1,
        pageSize: Int = 30,
        onResult: (List<FundRankItem>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://fund.eastmoney.com/data/rankhandler.aspx" +
                "?op=ph&dt=kf&ft=$fundType&rs=&gs=0&sc=$sortBy&st=desc" +
                "&sd=2025-01-01&ed=2026-12-31&qdii=&tabSubtype=,,,,,&pi=$page&pn=$pageSize&dx=1" +
                "&v=${System.currentTimeMillis()}"
        val req = Request.Builder().url(url)
            .addHeader("Referer", "https://fund.eastmoney.com/data/fundranking.html").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError("网络错误: ${e.message}") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    onResult(parseFundRankData(body))
                } catch (e: Exception) { onError("解析失败: ${e.message}") }
            }
        })
    }

    private fun parseFundRankData(raw: String): List<FundRankItem> {
        val results = mutableListOf<FundRankItem>()
        try {
            val datasStart = raw.indexOf("datas:[")
            if (datasStart < 0) return results
            val arrayStart = raw.indexOf("[", datasStart)
            val arrayEnd = raw.indexOf("]", arrayStart)
            if (arrayStart < 0 || arrayEnd < 0) return results
            val datasStr = raw.substring(arrayStart + 1, arrayEnd)
            val regex = Regex("\"([^\"]+)\"")
            for (match in regex.findAll(datasStr)) {
                val f = match.groupValues[1].split(",")
                if (f.size < 20) continue
                try {
                    results.add(FundRankItem(
                        code = f[0], name = f[1], type = f[3],
                        nav = f[4].toDoubleOrNull() ?: 0.0,
                        accNav = f[5].toDoubleOrNull() ?: 0.0,
                        dayChange = f[6].toDoubleOrNull() ?: 0.0,
                        weekChange = f[7].toDoubleOrNull() ?: 0.0,
                        monthChange = f[8].toDoubleOrNull() ?: 0.0,
                        threeMonthChange = f[9].toDoubleOrNull() ?: 0.0,
                        sixMonthChange = f[10].toDoubleOrNull() ?: 0.0,
                        yearChange = f[11].toDoubleOrNull() ?: 0.0,
                        twoYearChange = f[12].toDoubleOrNull() ?: 0.0,
                        threeYearChange = f[13].toDoubleOrNull() ?: 0.0,
                        thisYearChange = f[14].toDoubleOrNull() ?: 0.0,
                        sinceInception = f[15].toDoubleOrNull() ?: 0.0,
                    ))
                } catch (_: Exception) {}
            }
        } catch (e: Exception) { Log.e(TAG, "parseFundRankData error", e) }
        return results
    }

    // ==================== 基金历史净值 ====================
    fun fetchNavHistory(
        fundCode: String,
        perPage: Int = 30,
        startDate: String = "",
        endDate: String = "",
        onResult: (List<NavPoint>) -> Unit,
        onError: (String) -> Unit
    ) {
        // 使用重定向后的正确域名
        var url = "https://fundf10.eastmoney.com/F10DataApi.aspx?type=lsjz&code=$fundCode&page=1&per=$perPage"
        if (startDate.isNotBlank()) url += "&sdate=$startDate"
        if (endDate.isNotBlank()) url += "&edate=$endDate"
        Log.d(TAG, "fetchNavHistory: per=$perPage, $startDate~$endDate")
        request(url, { body ->
            try {
                val points = parseNavHistoryHtml(body)
                Log.d(TAG, "净值解析: ${points.size}条, code=$fundCode")
                onResult(points)
            } catch (e: Exception) { onError("解析净值数据失败: ${e.message}") }
        }, onError)
    }

    private fun parseNavHistoryHtml(raw: String): List<NavPoint> {
        val points = mutableListOf<NavPoint>()
        // 按行匹配 <tr>...</tr>
        val trRegex = Regex("<tr>(.*?)</tr>", RegexOption.DOT_MATCHES_ALL)
        val tdRegex = Regex("<td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
        for (trMatch in trRegex.findAll(raw)) {
            val tds = tdRegex.findAll(trMatch.groupValues[1]).map {
                it.groupValues[1].trim().replace(Regex("<[^>]+>"), "") // 去掉内嵌HTML标签
            }.toList()
            // 每行7列: 日期, 单位净值, 累计净值, 日增长率, 申购状态, 赎回状态, 分红
            if (tds.size >= 4) {
                val date = tds[0]
                if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) continue
                val nav = tds[1].toDoubleOrNull() ?: continue
                val accNav = tds[2].toDoubleOrNull() ?: nav
                val change = tds[3].replace("%", "").toDoubleOrNull() ?: 0.0
                points.add(NavPoint(date = date, nav = nav, accNav = accNav, changePct = change))
            }
        }
        Log.d(TAG, "parseNavHistoryHtml: 解析出 ${points.size} 条净值数据")
        return points
    }

    // ==================== 基金详情（规模、成立日期、经理、费率等） ====================

    // ==================== 全量净值数据（解决分页限制问题） ====================
    /**
     * 从pingzhongdata获取全量净值（Data_netWorthTrend）
     * 一次性获取成立以来全部数据，客户端按周期截取
     */
    fun fetchAllNavData(
        fundCode: String,
        onResult: (List<NavPoint>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://fund.eastmoney.com/pingzhongdata/$fundCode.js?v=${System.currentTimeMillis()}"
        request(url, { body ->
            try {
                val points = parseNetWorthTrend(body)
                Log.d(TAG, "全量净值: ${points.size}条, code=$fundCode")
                onResult(points)
            } catch (e: Exception) {
                Log.e(TAG, "解析全量净值失败", e)
                onError("解析失败: ${e.message}")
            }
        }, onError)
    }

    private fun parseNetWorthTrend(raw: String): List<NavPoint> {
        val points = mutableListOf<NavPoint>()
        val start = raw.indexOf("Data_netWorthTrend =")
        if (start < 0) return points
        val arrayStart = raw.indexOf("[", start)
        val arrayEnd = raw.indexOf("];", arrayStart)
        if (arrayStart < 0 || arrayEnd < 0) return points
        val arrayStr = raw.substring(arrayStart, arrayEnd + 1)
        val regex = Regex("\"x\":([0-9]+),\"y\":([0-9.]+),\"equityReturn\":([0-9.-]+)")
        for (match in regex.findAll(arrayStr)) {
            val timestamp = match.groupValues[1].toLongOrNull() ?: continue
            val nav = match.groupValues[2].toDoubleOrNull() ?: continue
            val changePct = match.groupValues[3].toDoubleOrNull() ?: 0.0
            val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(java.util.Date(timestamp))
            points.add(NavPoint(date = date, nav = nav, accNav = nav, changePct = changePct))
        }
        return points
    }

    // ==================== 板块行情 ====================
    fun fetchSectorList(
        pageSize: Int = 50,
        onResult: (List<SectorItem>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://push2.eastmoney.com/api/qt/clist/get" +
                "?pn=1&pz=$pageSize&po=1&np=1&fltt=2&invt=2&fid=f3&fs=m:90+t:3" +
                "&fields=f2,f3,f12,f14"
        request(url, { body ->
            try {
                val map = gson.fromJson<Map<String, Any>>(body,
                    object : TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val data = map["data"] as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val diff = data?.get("diff") as? List<Map<String, Any>>
                val sectors = diff?.map { item ->
                    SectorItem(
                        name = (item["f14"] as? String) ?: "",
                        code = (item["f12"] as? String) ?: "",
                        changePct = (item["f3"] as? Double) ?: 0.0
                    )
                } ?: emptyList()
                onResult(sectors)
            } catch (e: Exception) { onError("解析板块数据失败: ${e.message}") }
        }, onError)
    }

    // ==================== AI预测API（Render云端） ====================
    private const val AI_API_BASE = "https://fundpicker-api.onrender.com"

    /**
     * 从云端AI API获取预测结果（任意基金代码）
     */
    fun fetchAiPredictionFromApi(
        fundCode: String,
        horizon: Int = 30,
        onResult: (Map<String, Any>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$AI_API_BASE/predict/$fundCode?horizon=$horizon"
        request(url, { body ->
            try {
                val map = gson.fromJson<Map<String, Any>>(body,
                    object : TypeToken<Map<String, Any>>() {}.type)
                onResult(map)
            } catch (e: Exception) { onError("解析失败: ${e.message}") }
        }, onError)
    }

    // ==================== AI预测结果（从GitHub JSON） ====================
    /**
     * 从GitHub Pages获取批量预测结果
     */
    fun fetchAiPredictions(
        onResult: (Map<String, Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://raw.githubusercontent.com/yuyanpsy/psymap/main/docs/predictions.json"
        request(url, { body ->
            try {
                val map = gson.fromJson<Map<String, Any>>(body,
                    object : TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val predictions = map["predictions"] as? Map<String, Map<String, Any>> ?: emptyMap()
                onResult(predictions)
            } catch (e: Exception) {
                Log.e(TAG, "解析预测数据失败", e)
                onError("解析失败: ${e.message}")
            }
        }, onError)
    }

    // ==================== 基金搜索（精确搜索任意基金） ====================
    fun searchFund(
        keyword: String,
        onResult: (List<FundRankItem>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?callback=&m=1&key=$keyword"
        request(url, { body ->
            try {
                val map = gson.fromJson<Map<String, Any>>(body,
                    object : TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val datas = map["Datas"] as? List<Map<String, Any>> ?: emptyList()
                val results = datas.mapNotNull { item ->
                    val code = item["CODE"] as? String ?: return@mapNotNull null
                    val name = item["NAME"] as? String ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val baseInfo = item["FundBaseInfo"] as? Map<String, Any>
                    val nav = (baseInfo?.get("DWJZ") as? Double) ?: 0.0
                    val ftype = (baseInfo?.get("FTYPE") as? String) ?: ""
                    FundRankItem(code = code, name = name, type = ftype, nav = nav)
                }
                onResult(results)
            } catch (e: Exception) { onError("搜索解析失败: ${e.message}") }
        }, onError)
    }

    // ==================== 基金详情 ====================
    /**
     * 获取基金详情数据
     * 接口: http://fund.eastmoney.com/pingzhongdata/{code}.js
     * 返回大量 var xxx = ... 的JS变量
     */
    fun fetchFundDetail(
        fundCode: String,
        onResult: (FundDetailData) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://fund.eastmoney.com/pingzhongdata/$fundCode.js?v=${System.currentTimeMillis()}"
        request(url, { body ->
            try {
                onResult(parseFundDetailJs(body, fundCode))
            } catch (e: Exception) {
                Log.e(TAG, "解析基金详情失败", e)
                onError("解析失败: ${e.message}")
            }
        }, onError)
    }

    private fun parseFundDetailJs(raw: String, code: String): FundDetailData {
        fun extractQuoted(name: String): String {
            val regex = Regex("var\\s+$name\\s*=\\s*\"([^\"]*)\";")
            return regex.find(raw)?.groupValues?.get(1) ?: ""
        }
        fun extractVar(name: String): String {
            val regex = Regex("var\\s+$name\\s*=\\s*([^;]+);")
            return regex.find(raw)?.groupValues?.get(1)?.trim() ?: ""
        }

        // 基金经理（可能多个）
        val managerStr = extractVar("Data_currentFundManager")
        val managerNames = Regex("\"name\":\"([^\"]+)\"").findAll(managerStr).map { it.groupValues[1] }.toList()
        val managerWorkTimes = Regex("\"workTime\":\"([^\"]+)\"").findAll(managerStr).map { it.groupValues[1] }.toList()
        val managerSizes = Regex("\"fundSize\":\"([^\"]+)\"").findAll(managerStr).map { it.groupValues[1] }.toList()

        // 基金规模（从Data_fluctuationScale提取最新）
        val scaleStr = extractVar("Data_fluctuationScale")
        val scaleValues = Regex("\"y\":([\\d.]+)").findAll(scaleStr).map { it.groupValues[1] }.toList()
        val latestScale = scaleValues.lastOrNull() ?: ""

        // 成立日期
        val setupDate = extractQuoted("fS_jjqsrq")

        // 基金类型
        val fundType = extractQuoted("fS_jjfl")

        // 申购费率
        val buyRate = extractQuoted("fund_sourceRate")
        val buyRateDiscount = extractQuoted("fund_Rate")

        // 持仓股票（从stockCodesNew提取）
        val stocksRaw = extractVar("stockCodesNew")
        val topStocks = mutableListOf<String>()
        if (stocksRaw.isNotBlank() && stocksRaw != "\"\"" && stocksRaw.contains("[")) {
            val stockRegex = Regex("\"([^\"]+)\"")
            for (m in stockRegex.findAll(stocksRaw)) {
                val parts = m.groupValues[1].split(",")
                if (parts.size >= 3) {
                    topStocks.add("${parts[1]} ${parts[2]}%")
                }
            }
        }

        // 资产配置（股票/债券/现金占比）
        val assetStr = extractVar("Data_assetAllocation")
        val stockRatios = Regex("\"股票占净比\".*?\"data\":\\[([\\d.,]+)]").find(assetStr)?.groupValues?.get(1) ?: ""
        val bondRatios = Regex("\"债券占净比\".*?\"data\":\\[([\\d.,]+)]").find(assetStr)?.groupValues?.get(1) ?: ""
        val cashRatios = Regex("\"现金占净比\".*?\"data\":\\[([\\d.,]+)]").find(assetStr)?.groupValues?.get(1) ?: ""

        val latestStockRatio = stockRatios.split(",").lastOrNull() ?: ""
        val latestBondRatio = bondRatios.split(",").lastOrNull() ?: ""
        val latestCashRatio = cashRatios.split(",").lastOrNull() ?: ""

        // 持有人结构
        val holderStr = extractVar("Data_holderStructure")
        val instRatios = Regex("\"机构持有比例\".*?\"data\":\\[([\\d.,]+)]").find(holderStr)?.groupValues?.get(1) ?: ""
        val latestInstRatio = instRatios.split(",").lastOrNull() ?: ""

        return FundDetailData(
            fundCode = code,
            managerName = managerNames.joinToString("、"),
            managerWorkTime = managerWorkTimes.firstOrNull() ?: "",
            managerSize = managerSizes.firstOrNull() ?: "",
            fundScale = if (latestScale.isNotBlank()) "${latestScale}亿" else "",
            setupDate = setupDate,
            fundType = fundType,
            buyRate = buyRate,
            buyRateDiscount = buyRateDiscount,
            topStocks = topStocks.take(10),
            stockRatio = latestStockRatio,
            bondRatio = latestBondRatio,
            cashRatio = latestCashRatio,
            instHoldRatio = latestInstRatio
        )
    }

    // ==================== 基金概况（费率、规模等完整信息） ====================
    fun fetchFundOverview(
        fundCode: String,
        onResult: (FundOverviewData) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://fundf10.eastmoney.com/jbgk_$fundCode.html"
        request(url, { body ->
            try {
                onResult(parseFundOverview(body, fundCode))
            } catch (e: Exception) {
                Log.e(TAG, "解析基金概况失败", e)
                onError("解析失败: ${e.message}")
            }
        }, onError)
    }

    private fun parseFundOverview(raw: String, code: String): FundOverviewData {
        fun extract(label: String): String {
            // 匹配 <th>label</th><td...>value</td>
            val regex = Regex("$label</th><td[^>]*>(.*?)</td>", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(raw) ?: return ""
            return match.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
        }
        return FundOverviewData(
            fullName = extract("基金全称"),
            fundType = extract("基金类型"),
            setupDate = extract("成立日期/规模").substringBefore("/").trim(),
            scale = extract("净资产规模"),
            manager = extract("基金管理人"),
            custodian = extract("基金托管人"),
            managerPerson = extract("基金经理人"),
            manageFeeRate = extract("管理费率"),
            custodianFeeRate = extract("托管费率"),
            salesServiceFeeRate = extract("销售服务费率"),
            maxBuyRate = extract("最高申购费率"),
            maxRedeemRate = extract("最高赎回费率"),
            benchmark = extract("业绩比较基准"),
            dividend = extract("成立来分红")
        )
    }

    // ==================== 市场指数 ====================
    fun fetchMarketIndices(
        onResult: (List<MarketIndex>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://push2.eastmoney.com/api/qt/ulist.np/get" +
                "?fltt=2&fields=f2,f3,f12,f14&secids=1.000001,0.399001,0.399006"
        request(url, { body ->
            try {
                val map = gson.fromJson<Map<String, Any>>(body,
                    object : TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val data = map["data"] as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val diff = data?.get("diff") as? List<Map<String, Any>>
                val indices = diff?.map { item ->
                    MarketIndex(
                        name = (item["f14"] as? String) ?: "",
                        value = (item["f2"] as? Double) ?: 0.0,
                        changePct = (item["f3"] as? Double) ?: 0.0
                    )
                } ?: emptyList()
                onResult(indices)
            } catch (e: Exception) { onError("解析指数数据失败: ${e.message}") }
        }, onError)
    }

    // ==================== 通用请求 ====================
    private fun request(url: String, onResult: (String) -> Unit, onError: (String) -> Unit) {
        val req = Request.Builder().url(url)
            .addHeader("Referer", "https://fund.eastmoney.com/")
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError("网络错误: ${e.message}") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) { onError("HTTP ${response.code}"); return }
                    onResult(body)
                } catch (e: Exception) { onError("读取响应失败: ${e.message}") }
            }
        })
    }
}

data class FundEstimate(
    val fundCode: String = "", val name: String = "", val navDate: String = "",
    val nav: Double = 0.0, val estimateNav: Double = 0.0,
    val estimateChangePct: Double = 0.0, val estimateTime: String = ""
)

data class FundRankItem(
    val code: String = "", val name: String = "", val type: String = "",
    val nav: Double = 0.0, val accNav: Double = 0.0, val dayChange: Double = 0.0,
    val weekChange: Double = 0.0, val monthChange: Double = 0.0,
    val threeMonthChange: Double = 0.0, val sixMonthChange: Double = 0.0,
    val yearChange: Double = 0.0, val twoYearChange: Double = 0.0,
    val threeYearChange: Double = 0.0, val thisYearChange: Double = 0.0,
    val sinceInception: Double = 0.0
)

/** 基金概况数据（来自jbgk页面） */
data class FundOverviewData(
    val fullName: String = "",
    val fundType: String = "",
    val setupDate: String = "",
    val scale: String = "",
    val manager: String = "",
    val custodian: String = "",
    val managerPerson: String = "",
    val manageFeeRate: String = "",
    val custodianFeeRate: String = "",
    val salesServiceFeeRate: String = "",
    val maxBuyRate: String = "",
    val maxRedeemRate: String = "",
    val benchmark: String = "",
    val dividend: String = ""
)

/** 基金详情数据 */
data class FundDetailData(
    val fundCode: String = "",
    val managerName: String = "",
    val managerWorkTime: String = "",
    val managerSize: String = "",
    val fundScale: String = "",
    val setupDate: String = "",
    val fundType: String = "",
    val buyRate: String = "",
    val buyRateDiscount: String = "",
    val topStocks: List<String> = emptyList(),
    val stockRatio: String = "",
    val bondRatio: String = "",
    val cashRatio: String = "",
    val instHoldRatio: String = ""
)

/** 板块行情项 */
data class SectorItem(
    val name: String = "",
    val code: String = "",
    val changePct: Double = 0.0
)
