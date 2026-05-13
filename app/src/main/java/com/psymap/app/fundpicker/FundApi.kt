package com.psymap.app.fundpicker

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 基金数据API封装
 * 数据源：新浪财经（排行/分类）+ 东方财富（净值走势/详情）+ Supabase（AI预测）
 */
object FundApi {

    private const val TAG = "FundApi"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    // ==================== 行业板块定义（参考招商银行/新浪财经分类） ====================
    val SECTOR_LIST = listOf(
        "科技" to listOf("科技", "信息", "互联网", "数字经济", "数字", "电子", "计算机",
            "软件", "通信", "5G", "智联", "智选", "互联", "TMT", "云计算", "大数据",
            "硬件", "传媒", "游戏", "信创"),
        "半导体" to listOf("半导体", "芯片", "集成电路", "存储"),
        "人工智能" to listOf("人工智能", "AI", "智能", "机器人", "算力", "元宇宙"),
        "医药" to listOf("医药", "医疗", "健康", "生物", "创新药", "中药", "疫苗", "CXO",
            "医学", "养老"),
        "新能源" to listOf("新能源", "光伏", "风电", "碳中和", "清洁能源", "绿色电力",
            "核电", "氢能", "储能"),
        "消费" to listOf("消费", "食品", "饮料", "白酒", "家电", "零售", "必选", "品牌",
            "乐享生活", "美好生活", "生活", "乳业", "餐饮", "旅游"),
        "金融" to listOf("金融", "银行", "证券", "保险", "非银", "资本"),
        "军工" to listOf("军工", "国防", "航天", "航空", "军事"),
        "新能车" to listOf("新能车", "汽车", "智能驾驶", "电动车", "锂电", "新能源车"),
        "制造" to listOf("制造", "工业", "机械", "装备", "智造", "高端制造"),
        "地产" to listOf("地产", "房地产", "基建", "建筑", "建材"),
        "资源" to listOf("资源", "有色", "钢铁", "煤炭", "化工", "材料", "稀土", "黄金"),
        "农业" to listOf("农业", "农林", "养殖", "种业"),
        "环保" to listOf("环保", "生态", "低碳", "节能"),
        "港股" to listOf("港股", "恒生", "H股", "沪港深", "港深"),
        "海外" to listOf("海外", "QDII", "美国", "全球", "亚太", "纳斯达克", "标普",
            "日本", "德国", "印度", "越南", "东南亚"),
        "红利" to listOf("红利", "高股息", "分红"),
        "价值" to listOf("价值", "蓝筹"),
        "成长" to listOf("成长", "企业成长", "高成长"),
        "创新" to listOf("创新驱动", "创新成长", "创新动力", "创新"),
        "指数" to listOf("沪深300", "中证500", "中证1000", "上证50", "指数", "ETF",
            "50ETF", "沪深", "MSCI"),
        "债券" to listOf("债券", "纯债", "信用债", "利率债", "可转债"),
        "混合" to listOf("混合", "灵活配置", "平衡", "均衡", "回报", "精选", "优选",
            "甄选", "优质", "策略")
    )

    /** 按行业关键词筛选基金 */
    fun filterFundsBySector(funds: List<Fund>, sectorName: String): List<Fund> {
        val keywords = SECTOR_LIST.find { it.first == sectorName }?.second ?: return emptyList()
        return funds.filter { fund -> keywords.any { kw -> fund.name.contains(kw) } }
    }

    /** 根据基金名称分类到板块（返回第一个匹配的板块名） */
    fun classifyFundSector(fundName: String): String {
        if (fundName.isBlank()) return ""
        // 按板块顺序匹配（特定优先，比如"半导体"优于"科技"）
        for ((sector, keywords) in SECTOR_LIST) {
            if (keywords.any { kw -> fundName.contains(kw) }) {
                return sector
            }
        }
        return ""
    }

    /**
     * 东方财富移动端API获取基金排行（数据最全，支持大量返回）
     * 然后按关键词筛选
     */
    fun fetchFundsByKeywords(
        keywords: List<String>,
        onResult: (List<Fund>) -> Unit,
        onError: (String) -> Unit
    ) {
        // 获取1000只基金（5页x200只），然后本地按关键词筛选
        val url = "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNRank" +
                "?fundtype=25&SortColumn=SYL_1N&Sort=desc&pageIndex=1&pageSize=200" +
                "&deviceid=android&plat=Android&product=EFund&Version=6.0.0"
        request(url, { body ->
            try {
                val map = gson.fromJson<Map<String, Any>>(body,
                    object : TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val datas = map["Datas"] as? List<Map<String, Any>> ?: emptyList()
                val allFunds = datas.map { item ->
                    Fund(
                        code = item["FCODE"] as? String ?: "",
                        name = item["SHORTNAME"] as? String ?: "",
                        nav = (item["DWJZ"] as? String)?.toDoubleOrNull() ?: 0.0,
                        dayChange = (item["RZDF"] as? String)?.toDoubleOrNull() ?: 0.0,
                        weekChange = (item["SYL_Z"] as? String)?.toDoubleOrNull() ?: 0.0,
                        monthChange = (item["SYL_Y"] as? String)?.toDoubleOrNull() ?: 0.0,
                        threeMonthChange = (item["SYL_3Y"] as? String)?.toDoubleOrNull() ?: 0.0,
                        sixMonthChange = (item["SYL_6Y"] as? String)?.toDoubleOrNull() ?: 0.0,
                        yearChange = (item["SYL_1N"] as? String)?.toDoubleOrNull() ?: 0.0
                    )
                }
                // 按关键词筛选
                val filtered = allFunds.filter { fund ->
                    keywords.any { kw -> fund.name.contains(kw) }
                }
                Log.d(TAG, "移动端API: ${allFunds.size}只, 筛选后${filtered.size}只")
                onResult(filtered)
            } catch (e: Exception) { onError("解析失败: ${e.message}") }
        }, onError)
    }

    // ==================== 新浪财经基金排行 ====================
    /**
     * @param type2 基金类型: 0=全部, 2=股票型, 3=混合型, 4=债券型, 5=指数型
     * @param sort 排序: form_year/one_year/six_month/three_month
     * @param asc 0=降序, 1=升序
     */
    fun fetchSinaFundRank(
        type2: Int = 0,
        sort: String = "one_year",
        asc: Int = 0,
        page: Int = 1,
        num: Int = 100,
        onResult: (List<Fund>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://vip.stock.finance.sina.com.cn/fund_center/data/jsonp.php/" +
                "IO.XSRV2.CallbackList/NetValueReturn_Service.NetValueReturnOpen" +
                "?page=$page&num=$num&sort=$sort&asc=$asc&ccode=&type2=$type2&type3=&type4="
        val req = Request.Builder().url(url)
            .addHeader("Referer", "https://finance.sina.com.cn/")
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError("网络错误: ${e.message}") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.bytes()?.toString(Charsets.UTF_8) ?: ""
                    val jsonStr = body.substringAfter("CallbackList(").substringBeforeLast(")")
                    if (jsonStr.isBlank()) { onError("空响应"); return }
                    val map = gson.fromJson<Map<String, Any>>(jsonStr,
                        object : TypeToken<Map<String, Any>>() {}.type)
                    @Suppress("UNCHECKED_CAST")
                    val dataList = map["data"] as? List<Map<String, Any>> ?: emptyList()
                    val funds = dataList.map { item ->
                        Fund(
                            code = item["symbol"] as? String ?: "",
                            name = item["sname"] as? String ?: "",
                            nav = (item["per_nav"] as? String)?.toDoubleOrNull() ?: 0.0,
                            threeMonthChange = (item["three_month"] as? Double) ?: 0.0,
                            sixMonthChange = (item["six_month"] as? Double) ?: 0.0,
                            yearChange = (item["one_year"] as? Double) ?: 0.0,
                            fundSize = item["zmjgm"] as? String ?: "",
                            manager = item["jjjl"] as? String ?: ""
                        )
                    }
                    onResult(funds)
                } catch (e: Exception) {
                    Log.e(TAG, "新浪基金解析失败", e)
                    onError("解析失败: ${e.message}")
                }
            }
        })
    }

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

    // ==================== 板块详情（含资金流向） ====================
    fun fetchSectorDetail(
        pageSize: Int = 50,
        sortField: String = "f3",  // f3=涨幅 f62=主力净流入
        period: String = "1",      // 1=1日 5=5日 20=20日
        onResult: (List<SectorDetail>) -> Unit,
        onError: (String) -> Unit
    ) {
        // 根据时间维度选择不同的排序字段
        val fid = if (period == "1") sortField else "f${if (sortField == "f62") 267 else 3}"
        val fields = when (period) {
            "5" -> "f2,f3,f12,f14,f62,f184,f66,f69,f72,f75,f78,f164,f174"
            "20" -> "f2,f3,f12,f14,f62,f184,f66,f69,f72,f75,f78,f164,f174"
            else -> "f2,f3,f12,f14,f62,f184,f66,f69,f72,f75,f78"
        }
        val url = "https://push2.eastmoney.com/api/qt/clist/get" +
                "?pn=1&pz=$pageSize&po=1&np=1&fltt=2&invt=2&fid=$fid&fs=m:90+t:3" +
                "&fields=$fields"
        request(url, { body ->
            try {
                val map = gson.fromJson<Map<String, Any>>(body,
                    object : TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val data = map["data"] as? Map<String, Any>
                @Suppress("UNCHECKED_CAST")
                val diff = data?.get("diff") as? List<Map<String, Any>>
                val sectors = diff?.map { item ->
                    SectorDetail(
                        name = (item["f14"] as? String) ?: "",
                        code = (item["f12"] as? String) ?: "",
                        changePct = (item["f3"] as? Double) ?: 0.0,
                        mainNetInflow = ((item["f62"] as? Double) ?: 0.0) / 100000000.0, // 转亿
                        price = (item["f2"] as? Double) ?: 0.0,
                        turnoverRate = (item["f184"] as? Double) ?: 0.0
                    )
                } ?: emptyList()
                onResult(sectors)
            } catch (e: Exception) { onError("解析板块详情失败: ${e.message}") }
        }, onError)
    }

    // ==================== 板块关联基金 ====================
    /**
     * 获取某板块的相关基金
     * 通过东方财富板块关联基金接口
     */
    fun fetchSectorFunds(
        sectorName: String,
        onResult: (List<SectorFund>) -> Unit,
        onError: (String) -> Unit
    ) {
        // 使用基金搜索接口，搜索板块名称相关的基金
        val url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?callback=&m=1&key=$sectorName"
        request(url, { body ->
            try {
                val map = gson.fromJson<Map<String, Any>>(body,
                    object : TypeToken<Map<String, Any>>() {}.type)
                @Suppress("UNCHECKED_CAST")
                val datas = map["Datas"] as? List<Map<String, Any>> ?: emptyList()
                val funds = datas.mapNotNull { item ->
                    val code = item["CODE"] as? String ?: return@mapNotNull null
                    val name = item["NAME"] as? String ?: return@mapNotNull null
                    @Suppress("UNCHECKED_CAST")
                    val baseInfo = item["FundBaseInfo"] as? Map<String, Any>
                    val nav = (baseInfo?.get("DWJZ") as? Double) ?: 0.0
                    val dayChange = (baseInfo?.get("RZDF") as? Double) ?: 0.0
                    SectorFund(
                        fundCode = code,
                        fundName = name,
                        nav = nav,
                        dayChange = dayChange
                    )
                }
                onResult(funds)
            } catch (e: Exception) { onError("搜索板块基金失败: ${e.message}") }
        }, onError)
    }

    /**
     * 获取板块关联基金（通过排行榜筛选）
     * 搜索包含板块关键词的基金
     */
    fun fetchSectorRelatedFunds(
        sectorName: String,
        pageSize: Int = 30,
        onResult: (List<SectorFund>) -> Unit,
        onError: (String) -> Unit
    ) {
        // 先搜索板块名称相关基金
        fetchSectorFunds(sectorName,
            onResult = { funds ->
                if (funds.isNotEmpty()) {
                    onResult(funds.take(pageSize))
                } else {
                    // 降级：搜索简化关键词
                    val shortName = sectorName.take(2)
                    fetchSectorFunds(shortName, onResult = { onResult(it.take(pageSize)) }, onError = onError)
                }
            },
            onError = onError
        )
    }

    // ==================== AI预测API（Render云端） ====================
    private const val AI_API_BASE = "https://fundpicker-api.onrender.com"

    /** 触发后台全量预测 */
    fun triggerUpdate(onResult: (Map<String, Any>) -> Unit, onError: (String) -> Unit) {
        request("$AI_API_BASE/trigger-update", { body ->
            try {
                val map = gson.fromJson<Map<String, Any>>(body,
                    object : TypeToken<Map<String, Any>>() {}.type)
                onResult(map)
            } catch (e: Exception) { onError(e.message ?: "") }
        }, onError)
    }

    /** 获取TOP10预测结果（从Supabase） */
    fun fetchTop10(onResult: (List<Map<String, Any>>) -> Unit, onError: (String) -> Unit) {
        val url = "https://edzsmjegnkrbedqpotgu.supabase.co/rest/v1/fund_predictions?id=eq.latest&select=top10"
        val req = Request.Builder().url(url)
            .addHeader("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkenNtamVnbmtyYmVkcXBvdGd1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYzMDA5NDcsImV4cCI6MjA5MTg3Njk0N30.J1gHxRiRgEBSMtd3WwhmkwiO2bIpNJy2LDsphD0SPQU")
            .addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkenNtamVnbmtyYmVkcXBvdGd1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYzMDA5NDcsImV4cCI6MjA5MTg3Njk0N30.J1gHxRiRgEBSMtd3WwhmkwiO2bIpNJy2LDsphD0SPQU")
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError(e.message ?: "") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val list = gson.fromJson<List<Map<String, Any>>>(body,
                        object : TypeToken<List<Map<String, Any>>>() {}.type)
                    @Suppress("UNCHECKED_CAST")
                    val top10 = list?.firstOrNull()?.get("top10") as? List<Map<String, Any>> ?: emptyList()
                    Log.d(TAG, "Supabase TOP10: ${top10.size}只")
                    onResult(top10)
                } catch (e: Exception) { onError(e.message ?: "") }
            }
        })
    }

    /**
     * 获取分档位回测胜率（Supabase fund_prediction_backtest 表）
     * 返回 [{ bucket: "70-80", bucket_min: 70, bucket_max: 80,
     *       total_count: N, win_count: N, win_rate: 0.58,
     *       avg_actual_return: 3.2, sample_start_date, sample_end_date }]
     */
    fun fetchBacktest(
        horizon: Int = 30,
        onResult: (List<Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://edzsmjegnkrbedqpotgu.supabase.co/rest/v1/fund_prediction_backtest" +
                "?horizon_days=eq.$horizon&order=bucket_min.asc"
        val req = Request.Builder().url(url)
            .addHeader("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkenNtamVnbmtyYmVkcXBvdGd1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYzMDA5NDcsImV4cCI6MjA5MTg3Njk0N30.J1gHxRiRgEBSMtd3WwhmkwiO2bIpNJy2LDsphD0SPQU")
            .addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkenNtamVnbmtyYmVkcXBvdGd1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYzMDA5NDcsImV4cCI6MjA5MTg3Njk0N30.J1gHxRiRgEBSMtd3WwhmkwiO2bIpNJy2LDsphD0SPQU")
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError(e.message ?: "") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val list = gson.fromJson<List<Map<String, Any>>>(body,
                        object : TypeToken<List<Map<String, Any>>>() {}.type)
                    onResult(list ?: emptyList())
                } catch (e: Exception) { onError(e.message ?: "") }
            }
        })
    }

    /**
     * 实时预测单只基金
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
     * 从Supabase获取批量预测结果（fund_predictions表）
     */
    fun fetchAiPredictions(
        onResult: (Map<String, Map<String, Any>>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://edzsmjegnkrbedqpotgu.supabase.co/rest/v1/fund_predictions?id=eq.latest&select=all_predictions"
        val req = Request.Builder().url(url)
            .addHeader("apikey", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkenNtamVnbmtyYmVkcXBvdGd1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYzMDA5NDcsImV4cCI6MjA5MTg3Njk0N30.J1gHxRiRgEBSMtd3WwhmkwiO2bIpNJy2LDsphD0SPQU")
            .addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVkenNtamVnbmtyYmVkcXBvdGd1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYzMDA5NDcsImV4cCI6MjA5MTg3Njk0N30.J1gHxRiRgEBSMtd3WwhmkwiO2bIpNJy2LDsphD0SPQU")
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Supabase预测获取失败: ${e.message}")
                onError("网络错误: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val list = gson.fromJson<List<Map<String, Any>>>(body,
                        object : TypeToken<List<Map<String, Any>>>() {}.type)
                    @Suppress("UNCHECKED_CAST")
                    val allPreds = list?.firstOrNull()?.get("all_predictions") as? Map<String, Map<String, Any>>
                    if (allPreds != null && allPreds.isNotEmpty()) {
                        Log.d(TAG, "Supabase预测加载成功: ${allPreds.size}只基金")
                        onResult(allPreds)
                    } else {
                        Log.w(TAG, "Supabase预测为空")
                        onError("预测数据为空")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Supabase预测解析失败: ${e.message}")
                    onError("解析失败: ${e.message}")
                }
            }
        })
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

    // ==================== 市场指数（新浪财经：A股+伦敦金） ====================
    fun fetchMarketIndices(
        onResult: (List<MarketIndex>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "https://hq.sinajs.cn/list=s_sh000001,s_sz399001,s_sz399006,hf_XAU"
        val req = Request.Builder().url(url)
            .addHeader("Referer", "https://finance.sina.com.cn/")
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError(e.message ?: "") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.bytes()?.toString(charset("GBK")) ?: ""
                    val indices = mutableListOf<MarketIndex>()
                    val nameMap = mapOf(
                        "sh000001" to "上证指数",
                        "sz399001" to "深证成指",
                        "sz399006" to "创业板指"
                    )
                    // 两种格式：
                    // A股指数: hq_str_s_sh000001="上证指数,4167.44,-12.64,-0.30,..."  -> parts[3]=涨跌%
                    // 伦敦金:  hq_str_hf_XAU="4724.02,4687.050,..."                 -> 自算涨跌%
                    val regex = Regex("hq_str_([A-Za-z_]+\\w+)=\"([^\"]+)\"")
                    for (match in regex.findAll(body)) {
                        val key = match.groupValues[1]
                        val parts = match.groupValues[2].split(",")
                        if (parts.size < 4) continue
                        if (key.startsWith("s_")) {
                            val code = key.removePrefix("s_")
                            indices.add(MarketIndex(
                                name = nameMap[code] ?: parts[0],
                                value = parts[1].toDoubleOrNull() ?: 0.0,
                                changePct = parts[3].toDoubleOrNull() ?: 0.0
                            ))
                        } else if (key == "hf_XAU") {
                            val price = parts[0].toDoubleOrNull() ?: 0.0
                            val prevClose = parts[1].toDoubleOrNull() ?: 0.0
                            val changePct = if (prevClose > 0) (price - prevClose) / prevClose * 100 else 0.0
                            if (price > 0) {
                                indices.add(MarketIndex(
                                    name = "现货黄金",
                                    value = price,
                                    changePct = changePct
                                ))
                            }
                        }
                    }
                    Log.d(TAG, "市场指数: ${indices.size}项, ${indices.map { it.name }}")
                    onResult(indices)
                } catch (e: Exception) { onError(e.message ?: "") }
            }
        })
    }

    /** 指数日K线数据点 */
    data class IndexKPoint(
        val date: String, val open: Double, val high: Double,
        val low: Double, val close: Double, val volume: Double = 0.0
    )

    /**
     * 获取指数/黄金日K线历史（默认30日）
     * @param symbol A股指数: sh000001/sz399001/sz399006，黄金: XAU
     */
    fun fetchIndexKLine(
        symbol: String,
        days: Int = 60,
        onResult: (List<IndexKPoint>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (symbol == "XAU") {
            // 新浪全球期货接口（伦敦金日K）
            fetchGoldKLine(days, onResult, onError)
            return
        }
        // A股指数：新浪财经 K线
        val url = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/" +
                "CN_MarketData.getKLineData?symbol=$symbol&scale=240&ma=no&datalen=$days"
        val req = Request.Builder().url(url)
            .addHeader("Referer", "https://finance.sina.com.cn/").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError(e.message ?: "") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val list = gson.fromJson<List<Map<String, String>>>(body,
                        object : TypeToken<List<Map<String, String>>>() {}.type)
                    val points = list.mapNotNull { item ->
                        val date = item["day"] ?: return@mapNotNull null
                        IndexKPoint(
                            date = date,
                            open = item["open"]?.toDoubleOrNull() ?: 0.0,
                            high = item["high"]?.toDoubleOrNull() ?: 0.0,
                            low = item["low"]?.toDoubleOrNull() ?: 0.0,
                            close = item["close"]?.toDoubleOrNull() ?: 0.0,
                            volume = item["volume"]?.toDoubleOrNull() ?: 0.0
                        )
                    }
                    onResult(points)
                } catch (e: Exception) { onError(e.message ?: "") }
            }
        })
    }

    /** 获取伦敦金日K（新浪期货服务） */
    private fun fetchGoldKLine(
        days: Int,
        onResult: (List<IndexKPoint>) -> Unit,
        onError: (String) -> Unit
    ) {
        val cal = java.util.Calendar.getInstance()
        val y = cal.get(java.util.Calendar.YEAR)
        val m = cal.get(java.util.Calendar.MONTH) + 1
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val varName = "_hq_XAU_${y}_${m}_${d}"
        val url = "https://stock2.finance.sina.com.cn/futures/api/jsonp.php/var%20$varName=/" +
                "GlobalFuturesService.getGlobalFuturesDailyKLine?symbol=XAU&_=$y-$m-$d"
        val req = Request.Builder().url(url)
            .addHeader("Referer", "https://finance.sina.com.cn/").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { onError(e.message ?: "") }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val jsonStr = body.substringAfter("=(").substringBeforeLast(")")
                    val list = gson.fromJson<List<Map<String, String>>>(jsonStr,
                        object : TypeToken<List<Map<String, String>>>() {}.type)
                    val all = list.mapNotNull { item ->
                        val date = item["date"] ?: return@mapNotNull null
                        IndexKPoint(
                            date = date,
                            open = item["open"]?.toDoubleOrNull() ?: 0.0,
                            high = item["high"]?.toDoubleOrNull() ?: 0.0,
                            low = item["low"]?.toDoubleOrNull() ?: 0.0,
                            close = item["close"]?.toDoubleOrNull() ?: 0.0
                        )
                    }
                    // 取最后 days 天
                    onResult(if (all.size > days) all.takeLast(days) else all)
                } catch (e: Exception) { onError(e.message ?: "") }
            }
        })
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
